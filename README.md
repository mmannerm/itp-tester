# Tracking Prevention Tester

Originally written for ITP 2.0 tests, can be used for other tracking prevention tests as well.

## Usage

1. Add the domains you want to test in `/etc/hosts`. For example

```
127.0.0.1 3rdpartytestwebkit.org
127.0.0.1 nottracker.org

127.0.0.1 example.com
127.0.0.1 advertising.example.com
```

2. Edit TestServer.java's static finals to have domains you want to test.

```java
    private final static String FIRST_PARTY_DOMAIN = "example.com";
    private final static String THIRD_PARTY_DOMAIN = "3rdpartytestwebkit.org"; //"advertising.example.com"; //"nottracker.org";
```

3. Compile and run TestServer.java (or with your favorite IDE)

```bash
export JAVA_HOME=`/usr/libexec/java_home -v 11` # Use Java 11
./gradlew clean shadowjar
java -jar build/libs/itp-tester-0.1.0-SNAPSHOT-all.jar 
```

 
4. Direct the browser you want to test to `http://example.com:8080`

## Definitions

- **top frame**: i.e. the main page of FIRST_PARTY_DOMAIN
- **frame referrer**: The REFERER header that is sent in the iframe-tag GET request
- **referrer**: The REFERER header that is sent in the tested request
- **cookie**: The COOKIE header that is sent in the tested request
