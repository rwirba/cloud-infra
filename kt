---
- name: Upgrade Apache ActiveMQ Classic to 5.19.7
  hosts: activemq_servers
  become: true
  gather_facts: true
  serial: 1

  vars:
    activemq_version: "5.19.7"
    activemq_home: /opt/activemq
    activemq_install_dir: "{{ activemq_home }}/apache-activemq-{{ activemq_version }}"
    activemq_current_link: "{{ activemq_home }}/current"
    activemq_service: activemq
    activemq_user: activemq
    activemq_group: activemq
    activemq_broker_port: 61616
    activemq_archive: "apache-activemq-{{ activemq_version }}-bin.tar.gz"
    activemq_download_url: >-
      https://archive.apache.org/dist/activemq/{{ activemq_version }}/{{ activemq_archive }}
    activemq_checksum_url: "{{ activemq_download_url }}.sha512"
    activemq_download_path: "/var/tmp/{{ activemq_archive }}"
    activemq_backup_dir: >-
      {{ activemq_home }}/backups/activemq-{{ ansible_date_time.iso8601_basic_short }}

  pre_tasks:
    - name: Confirm the current symlink exists
      ansible.builtin.stat:
        path: "{{ activemq_current_link }}"
        follow: false
      register: current_link

    - name: Stop when the installation does not use the expected current symlink
      ansible.builtin.assert:
        that:
          - current_link.stat.exists
          - current_link.stat.islnk
        fail_msg: >-
          {{ activemq_current_link }} must be a symbolic link to the current
          ActiveMQ installation before this playbook can safely upgrade it.

    - name: Resolve the existing ActiveMQ installation
      ansible.builtin.command: "readlink -f {{ activemq_current_link }}"
      register: current_install
      changed_when: false

    - name: Set upgrade state
      ansible.builtin.set_fact:
        activemq_previous_dir: "{{ current_install.stdout }}"
        activemq_upgrade_required: "{{ current_install.stdout != activemq_install_dir }}"

    - name: Confirm Java 11 or newer is installed
      ansible.builtin.shell: |
        set -o pipefail
        java -version 2>&1 | awk -F '[".]' '/version/ { if ($2 == "1") print $3; else print $2 }'
      args:
        executable: /bin/bash
      register: java_major
      changed_when: false
      failed_when: java_major.stdout | int < 11

  tasks:
    - name: Upgrade ActiveMQ
      when: activemq_upgrade_required | bool
      block:
        - name: Download ActiveMQ and verify its Apache SHA-512 checksum
          ansible.builtin.get_url:
            url: "{{ activemq_download_url }}"
            dest: "{{ activemq_download_path }}"
            checksum: "sha512:{{ activemq_checksum_url }}"
            mode: "0644"
          register: activemq_download

        - name: Extract the new ActiveMQ release
          ansible.builtin.unarchive:
            src: "{{ activemq_download_path }}"
            dest: "{{ activemq_home }}"
            remote_src: true
            creates: "{{ activemq_install_dir }}/bin/activemq"

        - name: Set ownership on the new installation
          ansible.builtin.file:
            path: "{{ activemq_install_dir }}"
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            recurse: true

        - name: Stop ActiveMQ before copying configuration and persistent data
          ansible.builtin.systemd_service:
            name: "{{ activemq_service }}"
            state: stopped

        - name: Create a timestamped rollback backup
          ansible.builtin.file:
            path: "{{ activemq_backup_dir }}"
            state: directory
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            mode: "0750"

        - name: Back up the existing configuration and KahaDB data
          ansible.builtin.copy:
            src: "{{ item.src }}"
            dest: "{{ activemq_backup_dir }}/{{ item.dest }}"
            remote_src: true
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            mode: preserve
          loop:
            - src: "{{ activemq_previous_dir }}/conf/activemq.xml"
              dest: activemq.xml
            - src: "{{ activemq_previous_dir }}/conf/login.config"
              dest: login.config
            - src: "{{ activemq_previous_dir }}/data/kahadb/"
              dest: kahadb/

        - name: Migrate the existing broker configuration
          ansible.builtin.copy:
            src: "{{ item }}"
            dest: "{{ activemq_install_dir }}/conf/{{ item | basename }}"
            remote_src: true
            owner: "{{ activemq_user }}"
            group: "{{ activemq_group }}"
            mode: preserve
          loop:
            - "{{ activemq_previous_dir }}/conf/activemq.xml"
            - "{{ activemq_previous_dir }}/conf/login.config"

        - name: Ensure the broker bean has the ID required by ActiveMQ 5.19.7
          ansible.builtin.replace:
            path: "{{ activemq_install_dir }}/conf/activemq.xml"
            regexp: '(<broker\s+)(?![^>]*\bid=)'
            replace: '\1id="broker" '
            backup: true    

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

        - name: Reload systemd and start ActiveMQ
          ansible.builtin.systemd_service:
            name: "{{ activemq_service }}"
            daemon_reload: true
            enabled: true
            state: started

        - name: Wait for the broker port
          ansible.builtin.wait_for:
            host: 127.0.0.1
            port: "{{ activemq_broker_port }}"
            delay: 3
            timeout: 90

        - name: Verify the systemd service is active
          ansible.builtin.command: "systemctl is-active {{ activemq_service }}"
          register: activemq_status
          changed_when: false
          failed_when: activemq_status.stdout | trim != 'active'

      rescue:
        - name: Restore the previous current symlink
          ansible.builtin.file:
            src: "{{ activemq_previous_dir }}"
            dest: "{{ activemq_current_link }}"
            state: link
            force: true

        - name: Restart the previous ActiveMQ installation
          ansible.builtin.systemd_service:
            name: "{{ activemq_service }}"
            daemon_reload: true
            state: restarted

        - name: Report failed upgrade and successful rollback attempt
          ansible.builtin.fail:
            msg: >-
              ActiveMQ {{ activemq_version }} failed to start or validate.
              The current symlink was restored to {{ activemq_previous_dir }}
              and the service restart was requested. Inspect
              {{ activemq_previous_dir }}/data/activemq.log and journalctl.

    - name: Report that ActiveMQ is already at the requested version
      ansible.builtin.debug:
        msg: "ActiveMQ {{ activemq_version }} is already active; no upgrade was needed."
      when: not (activemq_upgrade_required | bool)
