# Crypt4GH

## Overview

Crypt4GH is a standard file container format from the [Global Alliance for Genomics and Health](https://www.ga4gh.org/) (GA4GH) that allows genomic data to remain secure throughout their lifetime, from initial sequencing to sharing with professionals at external organizations. The format uses _envelope encryption_ to protect files both at rest and in transit.

The data itself is encrypted with a 256-bits symmetric key, which is randomly generated on-the-fly by a cryptographically secure pseudorandom number generator.
The encryption is based on the [ChaCha20-Poly1305](https://en.wikipedia.org/wiki/ChaCha20-Poly1305) cipher and message authentication code by Daniel J. Bernstein. 
Large files are divided into blocks of 64 KB, and these blocks are individually encrypted with the data encryption key and a random 96-bits nonce that is different for each block.
Each block packages the encrypted data along with the plain-text nonce and a 16-byte _message authentication code_ (MAC) which is calculated from the cipher text.
The MAC code is used to verify the integrity of the message and confirm that the data block has not been accidentally damaged or intentionally tampered with.

Compared to asymmetric encryption, symmetric encryption is faster and scales well for large files, but it requires the data encryption key to be shared securely between the communicating parties. In Crypt4GH, when the encrypted dataset is to be shared with a new user, the data encryption key is placed in a _header packet_, and this packet is encrypted with another symmetric key that is only known to the sender and the receiver.
This second symmetric key is derived using the X25519 [elliptic-curve Diffie–Hellman](https://en.wikipedia.org/wiki/Elliptic-curve_Diffie%E2%80%93Hellman) (ECDH) key exchange scheme, which allows two parties that have both generated Curve25519-based asymmetric key pairs to derive the same shared key
based on their own private key and the public key from the other party. For additional security, this ECDH-shared key is combined with the two public keys and hashed with the BLAKE2 algorithm before it is used to encrypt the header packet with ChaCha20-Poly1305.

With the envelope encryption scheme used by Crypt4GH, it is not necessary to decrypt and re-encrypt the full dataset every time the data is to be shared with a new user.
It is sufficient to simply encrypt a new header packet containing the data encryption key and ship that together with the encrypted data blocks. It is also possible to share the same encrypted file with multiple users by adding multiple header packets, each one encrypted for a specific target user.

This Java implementation of Crypt4GH started out in the NeIC Nordic collaboration to establish the Nordic FEGA nodes, and is now developed further and maintained by the FEGA Norway team developing the [Norwegian node of Federated EGA](https://ega.elixir.no/). The package can be used both as a library and a command-line tool to encrypt and decrypt files, and also to generate new private/public key pairs.

![](https://www.ga4gh.org/wp-content/uploads/Crypt4GH_comic.png)

## File structure
![](https://habrastorage.org/webt/yn/y2/pk/yny2pkp68sccx1vbvmodz-hfpzm.png)

## Specification
Current version of specs can be found [here](http://samtools.github.io/hts-specs/crypt4gh.pdf).

## Maven installation
To include this library in your own Maven project, add the following dependency to the `pom.xml` file:

```xml
    <dependency>
        <groupId>no.elixir</groupId>
        <artifactId>crypt4gh</artifactId>
        <version>{VERSION}</version>
    </dependency>
```

## Download
Pre-compiled JAR-files are available for download from [GitHub packages](https://github.com/ELIXIR-NO/FEGA-Norway/packages/2287184).


## Building
To build Crypt4GH with Gradle, run the following command in the root directory of the repository:
```
./gradlew lib:crypt4gh:build [-Pversion=<version>]
```
This will create two JAR-files in the "lib/crypt4gh/build/libs" directory: one named `crypt4gh.jar` and another "fat" JAR named `crypt4gh-tool.jar` that includes all the external dependencies needed to run Crypt4GH as a stand-alone command-line tool. If the version number is specified, it will be appended to the filenames and also included in the Manifest file.

## Usage
The `crypt4gh` command below is an alias for `java -jar path/to/crypt4gh-tool.jar`

```
$ crypt4gh
usage: crypt4gh [-d <arg> | -e <arg> | -g <arg> | -h | -v]  [-kf <arg>] [-kp <arg>] [-pk <arg>] [-sk <arg>]

Crypt4GH encryption/decryption tool

 -d,--decrypt <arg>    decrypt the file (specify file to decrypt)
 -e,--encrypt <arg>    encrypt the file (specify file to encrypt)
 -g,--generate <arg>   generate key pair (specify desired key name)
 -h,--help             print this message
 -kf,--keyform <arg>   key format to use for generated keys
                       (OpenSSL or Crypt4GH)
 -kp,--keypass <arg>   password for Crypt4GH private key
                       (will be prompted afterwards if skipped)
 -pk,--pubkey <arg>    public key to use (specify key file)
 -sk,--seckey <arg>    secret key to use (specify key file)
 -v,--version          print application's version

Read more about the format at
http://samtools.github.io/hts-specs/crypt4gh.pdf
```
