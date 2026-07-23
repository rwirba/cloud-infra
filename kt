---
- name: Run ActiveMQ pre-upgrade validation
  tags:
    - precheck
  block:
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
          role can safely perform the upgrade.

    - name: Resolve the current ActiveMQ installation
      ansible.builtin.command:
        argv:
          - readlink
          - -f
          - "{{ activemq_current_link }}"
      register: current_install
      changed_when: false

    - name: Set ActiveMQ upgrade facts
      ansible.builtin.set_fact:
        activemq_previous_dir: "{{ current_install.stdout | trim }}"
        activemq_upgrade_required: >-
          {{ (current_install.stdout | trim) != activemq_install_dir }}

    - name: Inspect the existing installation
      ansible.builtin.stat:
        path: "{{ activemq_previous_dir }}"
      register: previous_install

    - name: Inspect the existing activemq.xml
      ansible.builtin.stat:
        path: "{{ activemq_previous_dir }}/conf/activemq.xml"
      register: current_activemq_xml

    - name: Inspect the existing configuration directory
      ansible.builtin.stat:
        path: "{{ activemq_previous_dir }}/conf"
      register: current_conf

    - name: Inspect the existing KahaDB directory
      ansible.builtin.stat:
        path: "{{ activemq_previous_dir }}/data/kahadb"
      register: current_kahadb

    - name: Validate the existing ActiveMQ installation
      ansible.builtin.assert:
        that:
          - previous_install.stat.exists
          - previous_install.stat.isdir
          - current_conf.stat.exists
          - current_conf.stat.isdir
          - current_activemq_xml.stat.exists
          - current_activemq_xml.stat.isreg
          - current_kahadb.stat.exists
          - current_kahadb.stat.isdir
        fail_msg: >-
          {{ activemq_previous_dir }} is missing its expected configuration
          or KahaDB directory. No changes were made.

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
          The {{ activemq_service }} service does not start and stop
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

    - name: Check whether target version directory exists
      ansible.builtin.stat:
        path: "{{ activemq_install_dir }}"
      register: target_install

    - name: Stop when a stale target installation exists
      ansible.builtin.assert:
        that:
          - >-
            not (
              target_install.stat.exists
              and activemq_upgrade_required | bool
            )
        fail_msg: >-
          {{ activemq_install_dir }} already exists while the current
          symlink points to {{ activemq_previous_dir }}. This could be an
          incomplete earlier upgrade. Inspect it before continuing.

    - name: Calculate the current KahaDB size
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

    - name: Calculate required disk space
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
          Insufficient disk space. Required:
          {{ activemq_required_space | int | human_readable }}.
          Available:
          {{ available_disk.stdout | int | human_readable }}.

    - name: Display ActiveMQ precheck results
      ansible.builtin.debug:
        msg:
          - "Current installation: {{ activemq_previous_dir }}"
          - "Target installation: {{ activemq_install_dir }}"
          - "Service status: {{ initial_service_status.stdout | trim }}"
          - "Java version: {{ java_major.stdout | trim }}"
          - "KahaDB size: {{ kahadb_size.stdout.split()[0] | int | human_readable }}"
          - "Upgrade required: {{ activemq_upgrade_required | bool }}"

- name: Upgrade ActiveMQ
  tags:
    - upgrade
  when: activemq_upgrade_required | bool
  block:
    - name: Download ActiveMQ and verify SHA-512 checksum
      ansible.builtin.get_url:
        url: "{{ activemq_download_url }}"
        dest: "{{ activemq_download_path }}"
        checksum: "sha512:{{ activemq_checksum_url }}"
        owner: root
        group: root
        mode: "0644"

    - name: Extract the new ActiveMQ release
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

    - name: Create the rollback backup directory
      ansible.builtin.file:
        path: "{{ activemq_backup_dir }}"
        state: directory
        owner: "{{ activemq_user }}"
        group: "{{ activemq_group }}"
        mode: "0750"

    - name: Back up the complete ActiveMQ configuration
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

    - name: Migrate the complete ActiveMQ configuration
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

    - name: Create the target KahaDB directory
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

    - name: Point current symlink to the new version
      ansible.builtin.file:
        src: "{{ activemq_install_dir }}"
        dest: "{{ activemq_current_link }}"
        state: link
        force: true
        owner: "{{ activemq_user }}"
        group: "{{ activemq_group }}"

    - name: Reload systemd and start the new version
      ansible.builtin.systemd_service:
        name: "{{ activemq_service }}"
        daemon_reload: true
        enabled: true
        state: started

    - name: Wait for ActiveMQ connector ports
      ansible.builtin.wait_for:
        host: 127.0.0.1
        port: "{{ item }}"
        state: started
        delay: 2
        timeout: 120
      loop: "{{ activemq_connector_ports }}"

    - name: Confirm the systemd service is active
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
          The service started, but {{ activemq_current_link }} does not
          point to {{ activemq_install_dir }}.

    - name: Confirm the running process uses the new installation
      ansible.builtin.shell: |
        set -o pipefail
        ps -ef |
          grep '[a]ctivemq' |
          grep -F "{{ activemq_install_dir }}"
      args:
        executable: /bin/bash
      register: running_activemq_version
      changed_when: false

    - name: Report successful ActiveMQ upgrade
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
          {{
            ansible_failed_result.msg
            | default('Unknown ActiveMQ upgrade failure')
          }}

    - name: Stop the failed installation
      ansible.builtin.systemd_service:
        name: "{{ activemq_service }}"
        state: stopped
      failed_when: false

    - name: Restore the previous current symlink
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

    - name: Resolve the symlink after rollback
      ansible.builtin.command:
        argv:
          - readlink
          - -f
          - "{{ activemq_current_link }}"
      register: rollback_current
      changed_when: false

    - name: Validate the rollback symlink
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
  tags:
    - upgrade
