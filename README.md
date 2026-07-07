Prerequisites
OpenSSL installed on your computer.
The Base64-encoded certificate files.
The password for the .p12 file (if applicable).
Step 1 – Save the Base64 Content

Save the Base64 text into separate files.

Example:

certificate.p12.b64
certificate.cer.b64

Ensure the files contain only the Base64 text with no additional spaces or characters.

Step 2 – Decode the Base64 Files

Run the following commands to convert the Base64 text into the original binary files.

Decode the PKCS#12 (.p12) file
base64 -d certificate.p12.b64 > certificate.p12
Decode the Certificate (.cer) file
base64 -d certificate.cer.b64 > certificate.cer

You should now have:

certificate.p12
certificate.cer
Step 3 – Extract the Certificate and Private Key from the P12 File

Run:

openssl pkcs12 -in certificate.p12 -nodes -out certificate.pem

You will be prompted for the .p12 password.

The output file (certificate.pem) contains both:

The certificate
The private key
Step 4 – Extract Only the Private Key (Optional)

If you need only the private key:

openssl pkey -in certificate.pem -out private.key

The file will begin with:

-----BEGIN PRIVATE KEY-----
Step 5 – Extract Only the Certificate (Optional)

If you need only the certificate:

openssl x509 -in certificate.pem -out certificate.crt

The output will begin with:

-----BEGIN CERTIFICATE-----
Step 6 – Convert the CER File to PEM Format

If the .cer file does not already contain the PEM headers, convert it using:

openssl x509 -inform DER -in certificate.cer -out certificate_from_cer.pem

If the command returns an error, the .cer file may already be PEM encoded. In that case, use:

openssl x509 -in certificate.cer -out certificate_from_cer.pem
Step 7 – Verify the Output

To view the certificate details:

openssl x509 -in certificate.crt -text -noout

To verify the private key:

openssl pkey -in private.key -check
Expected Output Files
File	Purpose
certificate.p12	Decoded PKCS#12 file
certificate.cer	Decoded certificate
certificate.pem	Certificate and private key combined
certificate.crt	Certificate only
private.key	Private key only
PEM Headers

A valid certificate should begin with:

-----BEGIN CERTIFICATE-----

A valid private key should begin with:

-----BEGIN PRIVATE KEY-----

or

-----BEGIN RSA PRIVATE KEY-----

depending on the type of key.
