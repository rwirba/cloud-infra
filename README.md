f you have Git for Windows installed, open Git Bash.

Open Notepad.
Paste the Base64 content.

Save it as:

certificate.p12.b64
certificate.cer.b64

Choose Save as type: All Files (.) so Windows doesn't append .txt.

Open Git Bash in the folder containing the files.
Decode the files:
base64 -d certificate.p12.b64 > certificate.p12
base64 -d certificate.cer.b64 > certificate.cer
Convert the .p12:
openssl pkcs12 -in certificate.p12 -nodes -out certificate.pem
