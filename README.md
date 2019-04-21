# An example application for Phlegyas.

To build and run:

```
export PORT=12345
git clone https://github.com/dspearson/phlegyas-example
cd phlegyas-example
lein deps
lein uberjar
lein -jar target/uberjar/phlegyas-example-0.0.1-SNAPSHOT-standalone.jar $PORT
```

You now have a 9P2000 filesystem listening on localhost, with the port as specified above.
