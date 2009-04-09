-------------------------------------
-- Darkstar Smoke Test Application --
-------------------------------------

Simple server application to validate Darkstar client API's. Clients can 
connect to this server which will attempt to interact with the client, 
triggering all the messages in the SimpleSgsProtocol. 


The client should be implemented in such a way to "echo" back most of the 
messages. The Python client's SmokeTestClient.py file can be used as a guideline
for implementing a test client. A flowchart in PDF format is included for easier
visualisation of the process. Further details are mentioned below.

The server will log the progress of the test. If a test fails, then it will
be logged, and the invalid message as well as the expected message will be shown.
If the test passes, then this will be displayed in the log as well.


When a test failure occurs, the client won't be disconnected. However, the 
server will continue to log messages, and this can be used to assist in finding
bugs in the client.

A quick way to get this running is:
"mvn verify -Prun-server"

-----------------------------------
---- Implementing Test Clients ----
-----------------------------------


Test clients should initialize and perform the following actions:
1. Login with the "kickme" username -> Assert that the loginFailed callback is correctly hit.
2. Login with the "discme" username -> Assert that a forced disconnect callback is correctly hit.
3. Login with any other username

Then, the listeners need to be implemented to send messages to the server 
whenever a callback is triggered:
* ChannelListener:
  - receivedMessage: "receivedChannelMessage:" + channel.getName() + " " + message.tostring()
  - leftChannel: "leftChannel:" + channel.getName()

* SimpleClientListener:
  - loggedIn: "loggedIn:" + username
  - joinedChannel: "joinedChannel:" + channel.getName() (Channel message)
  - receivedMessage: 
    - if the message is "logout" then a graceful client-side logout should be initiated
    - otherwise, reply to the server with "receivedMessage:" + message.tostring()
  - reconnecting: "reconnecting:"
  - reconnected: "reconnected:" 

---------------------------------
--- Further Considerations ------
---------------------------------


- Implement multi-node smoke test (reconnection, redirection, ...).
- Test joining channels with large IDs (difficult with current API).
- Test sending and receiving large messages.