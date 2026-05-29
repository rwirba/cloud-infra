---
- name: Populate service facts
  ansible.builtin.service_facts:

- name: Extract installed Splunk version
  ansible.builtin.command: "{{ splunk_home }}/bin/splunk version"
  register: splunk_version_output
  failed_when: false
  changed_when: false
  when: "'splunk' in ansible_facts.packages"

- name: Set installed version fact
  ansible.builtin.set_fact:
    splunk_installed_version: "{{ splunk_version_output.stdout | regex_search('(\\d+\\.\\d+\\.\\d+)', '\\1') | first | default('0.0.0') }}"
  when: splunk_version_output.stdout is defined

- name: Download Splunk Universal Forwarder from Nexus
  ansible.builtin.get_url:
    url: "{{ nexus_url }}"
    dest: "/tmp/{{ file_name }}"
    mode: '0755'
  register: downloaded_file

- name: Extract version from downloaded package
  ansible.builtin.command: "dpkg-deb --showformat='${Version}' -W /tmp/{{ file_name }}"
  register: latest_version_output
  changed_when: false

- name: Set latest version fact
  ansible.builtin.set_fact:
    latest_splunk_version: "{{ latest_version_output.stdout | trim | default('0.0.0') }}"

- name: Debug versions
  ansible.builtin.debug:
    msg: |
      Installed version: {{ splunk_installed_version | default('Not installed') }}
      Latest version in Nexus: {{ latest_splunk_version }}

- name: Install/Upgrade Splunk Universal Forwarder
  ansible.builtin.apt:
    deb: "/tmp/{{ file_name }}"
    state: present
  when: >
    splunk_installed_version is not defined or
    latest_splunk_version is version(splunk_installed_version, '>')
