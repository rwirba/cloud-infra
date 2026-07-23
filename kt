---
- name: Upgrade Apache ActiveMQ Classic to 5.19.7
  hosts: activemq_servers
  become: true
  gather_facts: true
  serial: 1
  any_errors_fatal: true

  vars:
    activemq_version: "5.19.7"
    activemq_home: /opt/activemq
    activemq_install_dir: "{{ activemq_home }}/apache-activemq-{{ activemq_version }}"
    activemq_current_link: "{{ activemq_home }}/current"

    activemq_service: activemq
    activemq_user: activemq
    activemq_group: activemq

    activemq_connector_ports:
      - 61616  # OpenWire
      - 5672   # AMQP
      - 61613  # STOMP
      - 1883   # MQTT
      - 61614  # WebSocket

    activemq_archive: "apache-activemq-{{ activemq_version }}-bin.tar.gz"

    activemq_download_url: >-
      https://archive.apache.org/dist/activemq/{{ activemq_version }}/{{ activemq_archive }}

    activemq_checksum_url: "{{ activemq_download_url }}.sha512"
    activemq_download_path: "/var/tmp/{{ activemq_archive }}"

    activemq_backup_dir: >-
      {{ activemq_home }}/backups/activemq-{{ ansible_date_time.iso8601_basic_short }}

    # Extra space for release files and operational headroom.
    activemq_disk_safety_bytes: 1073741824

  pre_tasks:
    - name: Confirm the current symlink exists
      ansible.builtin.stat:
        path: "{{ activemq_current_link }}"
        follow: false
      register: current_link

    - name: Validate the current symlink
      ansible.builtin.assert:
        that:
          - current_link.stat.exists
          - current_link.stat.islnk
        fail_msg: >-
          {{ activemq_current_link }} must be a symbolic link before this
          playbook can safely perform the upgrade.

    - name: Resolve the current ActiveMQ installation
      ansible.builtin.command:
        argv:
          - readlink
          - -f
          - "{{ activemq_current_link }}"
      register: current_install
      changed_when: false

    - name: Set upgrade facts
      ansible.builtin.set_fact:
        activemq_previous_dir: "{{ current_install.stdout | trim }}"
        activemq_upgrade_required: >-
          {{ (current_install.stdout | trim) != activemq_install_dir }}

    - name: Inspect the existing installation
      ansible.builtin.stat:
        path: "{{ activemq_previous_dir }}"
      register: previous_install

    - name: Inspect the current configuration
      ansible.builtin.stat:
        path: "{{ activemq_previous_dir }}/conf/activemq.xml"
      register: current_activemq_xml

    - name: Inspect the current KahaDB directory
      ansible.builtin.stat:
        path: "{{ activemq_previous_dir }}/data/kahadb"
      register: current_kahadb

    - name: Validate the current ActiveMQ installation
      ansible.builtin.assert:
        that:
          - previous_install.stat.exists
          - previous_install.stat.isdir
          - current_activemq_xml.stat.exists
          - current_activemq_xml.stat.isreg
          - current_kahadb.stat.exists
          - current_kahadb.stat.isdir
        fail_msg: >-
          The installation under {{ activemq_previous_dir }} is missing its
          expected configuration or KahaDB directory. Upgrade stopped before
          making any changes.

    - name: Confirm the ActiveMQ user exists
      ansible.builtin.getent:
        database: passwd
        key: "{{ activemq_user }}"

    - name: Confirm the ActiveMQ group exists
      ansible.builtin.getent:
        database: group
        key: "{{ activemq_group }}"

    - name: Read the ActiveMQ systemd unit
      ansible.builtin.command:
        argv:
          - systemctl
          - cat
          - "{{ activemq_service }}"
      register: activemq_systemd_unit
      changed_when: false

    - name: Confirm systemd uses the current symlink
      ansible.builtin.assert:
        that:
          - >-
            activemq_current_link ~ '/bin/activemq start'
            in activemq_systemd_unit.stdout
          - >-
            activemq_current_link ~ '/bin/activemq stop'
            in activemq_systemd_unit.stdout
        fail_msg: >-
          The {{ activemq_service }} systemd service does not start and stop
          ActiveMQ through {{ activemq_current_link }}.

    - name: Confirm ActiveMQ is running before the upgrade
      ansible.builtin.command:
        argv:
          - systemctl
          - is-active
          - "{{ activemq_service }}"
      register: initial_service_status
      changed_when: false
      failed_when: initial_service_status.stdout | trim != "active"

    - name: Confirm Java 11 or newer is installed
      ansible.builtin.shell: |
        set -o pipefail
        java -version 2>&1 |
          awk -F '[".]' '/version/ {
            if ($2 == "1") {
              print $3
            } else {
              print $2
            }
          }'
      args:
        executable: /bin/bash
      register: java_major
      changed_when: false
      failed_when:
        - java_major.stdout | trim | length == 0 or
          java_major.stdout | int < 11

    - name: Check whether target version directory already exists
      ansible.builtin.stat:
        path: "{{ activemq_install_dir }}"
      register: target_install

    - name: Stop when a stale target installation exists
      ansible.builtin.assert:
        that:
          - not (target_install.stat.exists and activemq_upgrade_required | bool)
        fail_msg: >-
          {{ activemq_install_dir }} already exists while the current symlink
          points to {{ activemq_previous_dir }}. This may be an incomplete
          earlier upgrade. Inspect it before removing or reusing it.

    - name: Calculate current KahaDB size
      ansible.builtin.command:
        argv:
          - du
          - -sb
          - "{{ activemq_previous_dir }}/data/kahadb"
      register: kahadb_size
      changed_when: false

    - name: Check available disk space
      ansible.builtin.shell: |
        set -o pipefail
        df -PB1 "{{ activemq_home }}" | awk 'NR == 2 {print $4}'
      args:
        executable: /bin/bash
      register: available_disk
      changed_when: false

    - name: Calculate required free space
      ansible.builtin.set_fact:
        activemq_required_space: >-
          {{
            ((kahadb_size.stdout.split()[0] | int) * 2)
            + activemq_disk_safety_bytes
          }}

    - name: Validate available disk space
      ansible.builtin.assert:
        that:
          - available_disk.stdout | int > activemq_required_space | int
        fail_msg: >-
          Insufficient free disk space. The playbook requires approximately
          {{ activemq_required_space | int | human_readable(unit='G') }}
          but only
          {{ available_disk.stdout | int | human_readable(unit='G') }}
          is available.

    - name: Display production upgrade summary
      ansible.builtin.debug:
        msg:
          - "Current installation: {{ activemq_previous_dir }}"
          - "Target installation: {{ activemq_install_dir }}"
          - "Java version: {{ java_major.stdout | trim }}"
          - "Service status: {{ initial_service_status.stdout | trim }}"
          - "KahaDB size: {{ kahadb_size.stdout.split()[0] | int | human_readable }}"
          - "Upgrade required: {{ activemq_upgrade_required | bool }}"

  tasks:
    - name: Upgrade ActiveMQ
      when: activemq_upgrade_required | bool
      block:
        - name: Download ActiveMQ and verify Apache SHA-512 checksum
          ansible.builtin.get_url:
            url: "{{ activemq_download_url }}"
            dest: "{{ activemq_download_path }}"
            checksum: "sha512:{{ activemq_checksum_url }}"
            owner: root
            group: root
            mode: "0644"

        - name: Extract ActiveMQ 5.19.7
          ansible.builtin.unarchive:
            src: "{{ activemq_download_path }}"
            dest: "{{ activemq_home }}"
            remote_src: true
            creates: "{{ activemq_install_dir }}/bin/activemq"

        - name: Confirm the new executable exists
          ansible.builtin.stat:
            path: "{{ activemq_install_dir }}/bin/activemq"
          register: new_activemq_executable

        - name: Validate the extracted release
          ansible.builtin.assert:
            that:
              - new_activemq_executable.stat.exists
              - new_activemq_executable.stat.isreg
            fail_msg: >-
              ActiveMQ {{ activemq_version }} was not extracted correctly.

        - name: Set ownership on the new installation
          ansible.builtin.file:
            path: "{{ activemq_install_dir }}"
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            recurse: true

        - name: Stop ActiveMQ
          ansible.builtin.systemd_service:
            name: "{{ activemq_service }}"
            state: stopped

        - name: Confirm ActiveMQ stopped
          ansible.builtin.command:
            argv:
              - systemctl
              - is-active
              - "{{ activemq_service }}"
          register: stopped_status
          changed_when: false
          failed_when: >-
            stopped_status.stdout | trim not in ['inactive', 'failed']

        - name: Create timestamped rollback backup
          ansible.builtin.file:
            path: "{{ activemq_backup_dir }}"
            state: directory
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            mode: "0750"

        - name: Back up the complete configuration
          ansible.builtin.copy:
            src: "{{ activemq_previous_dir }}/conf/"
            dest: "{{ activemq_backup_dir }}/conf/"
            remote_src: true
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            mode: preserve

        - name: Back up KahaDB
          ansible.builtin.copy:
            src: "{{ activemq_previous_dir }}/data/kahadb/"
            dest: "{{ activemq_backup_dir }}/kahadb/"
            remote_src: true
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            mode: preserve

        - name: Migrate the complete configuration
          ansible.builtin.copy:
            src: "{{ activemq_previous_dir }}/conf/"
            dest: "{{ activemq_install_dir }}/conf/"
            remote_src: true
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            mode: preserve

        - name: Ensure the broker bean has the required ID
          ansible.builtin.replace:
            path: "{{ activemq_install_dir }}/conf/activemq.xml"
            regexp: '(<broker\s+)(?![^>]*\bid=)'
            replace: '\1id="broker" '
            backup: true

        - name: Create target KahaDB directory
          ansible.builtin.file:
            path: "{{ activemq_install_dir }}/data/kahadb"
            state: directory
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            mode: "0750"

        - name: Migrate KahaDB persistent data
          ansible.builtin.copy:
            src: "{{ activemq_previous_dir }}/data/kahadb/"
            dest: "{{ activemq_install_dir }}/data/kahadb/"
            remote_src: true
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            mode: preserve

        - name: Point current symlink to ActiveMQ 5.19.7
          ansible.builtin.file:
            src: "{{ activemq_install_dir }}"
            dest: "{{ activemq_current_link }}"
            state: link
            force: true
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"

        - name: Reload systemd and start ActiveMQ 5.19.7
          ansible.builtin.systemd_service:
            name: "{{ activemq_service }}"
            daemon_reload: true
            enabled: true
            state: started

        - name: Wait for configured connector ports
          ansible.builtin.wait_for:
            host: 127.0.0.1
            port: "{{ item }}"
            state: started
            delay: 2
            timeout: 120
          loop: "{{ activemq_connector_ports }}"

        - name: Confirm the service is active
          ansible.builtin.command:
            argv:
              - systemctl
              - is-active
              - "{{ activemq_service }}"
          register: upgraded_service_status
          changed_when: false
          failed_when: upgraded_service_status.stdout | trim != "active"

        - name: Resolve the upgraded current symlink
          ansible.builtin.command:
            argv:
              - readlink
              - -f
              - "{{ activemq_current_link }}"
          register: upgraded_current
          changed_when: false

        - name: Validate the upgraded symlink
          ansible.builtin.assert:
            that:
              - upgraded_current.stdout | trim == activemq_install_dir
            fail_msg: >-
              The service started but the current symlink does not point to
              {{ activemq_install_dir }}.

        - name: Confirm the running Java process uses ActiveMQ 5.19.7
          ansible.builtin.shell: |
            set -o pipefail
            ps -ef |
              grep '[a]ctivemq' |
              grep -F "{{ activemq_install_dir }}"
          args:
            executable: /bin/bash
          register: running_version
          changed_when: false

        - name: Report successful production upgrade
          ansible.builtin.debug:
            msg:
              - "ActiveMQ successfully upgraded to {{ activemq_version }}."
              - "Active installation: {{ upgraded_current.stdout | trim }}"
              - "Service status: {{ upgraded_service_status.stdout | trim }}"
              - "Rollback backup: {{ activemq_backup_dir }}"

      rescue:
        - name: Record the upgrade failure
          ansible.builtin.set_fact:
            activemq_upgrade_failure: >-
              {{ ansible_failed_result.msg | default('Unknown upgrade failure') }}

        - name: Stop the failed ActiveMQ installation
          ansible.builtin.systemd_service:
            name: "{{ activemq_service }}"
            state: stopped
          failed_when: false

        - name: Restore the previous symlink
          ansible.builtin.file:
            src: "{{ activemq_previous_dir }}"
            dest: "{{ activemq_current_link }}"
            state: link
            force: true
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"

        - name: Restart the previous ActiveMQ installation
          ansible.builtin.systemd_service:
            name: "{{ activemq_service }}"
            daemon_reload: true
            enabled: true
            state: restarted

        - name: Wait for OpenWire after rollback
          ansible.builtin.wait_for:
            host: 127.0.0.1
            port: 61616
            state: started
            delay: 2
            timeout: 120

        - name: Confirm the previous service is active
          ansible.builtin.command:
            argv:
              - systemctl
              - is-active
              - "{{ activemq_service }}"
          register: rollback_service_status
          changed_when: false
          failed_when: rollback_service_status.stdout | trim != "active"

        - name: Resolve the current symlink after rollback
          ansible.builtin.command:
            argv:
              - readlink
              - -f
              - "{{ activemq_current_link }}"
          register: rollback_current
          changed_when: false

        - name: Validate the restored symlink
          ansible.builtin.assert:
            that:
              - rollback_current.stdout | trim == activemq_previous_dir
            fail_msg: >-
              Automatic rollback did not restore the previous symlink.

        - name: Report failed upgrade and successful rollback
          ansible.builtin.fail:
            msg: >-
              ActiveMQ {{ activemq_version }} upgrade failed:
              {{ activemq_upgrade_failure }}.
              Automatic rollback succeeded. The service is active on
              {{ activemq_previous_dir }}. Review
              journalctl -u {{ activemq_service }} and
              {{ activemq_previous_dir }}/data/activemq.log.

    - name: Report that ActiveMQ is already upgraded
      ansible.builtin.debug:
        msg: >-
          {{ activemq_current_link }} already points to
          {{ activemq_install_dir }}. No upgrade was required.
      when: not (activemq_upgrade_required | bool)
