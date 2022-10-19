# Zendesk To Profiles

This program gets webhooks from Zendesk when tickets are updated. 

Upon receiving a webhook, the program checks for an attached SendSafely package.
If one exists, the contents of the package are checked to determine if the file is a query profile.

The program could also handle these files, if we can determine how to ideintify them:
* query.json
* gc.log

## How to install the program on your local Mac
* Checkout the code `git clone https://github.com/rcprcp/ZendeskToProfiles.git`
* `cd ZendeskToProfiles` 
* `mvn clean package`  This should create a standalone jar file with all dependencies.
* `java -jar target/ZendeskToProfiles-1.0-SNAPSHOT-jar-with-dependencies.jar`

## How to test or add a feature
These instructions are for setting up a local (on your mac) testing environment.  
In this way you can use the Intellij debugger, or even use something like YourKit profiler
in order to check the application's performance. 

* get ngrok `https://ngrok.com/` (only need to do this once) :)
* start ngrok (`ngrok http 5000`) This will return an "ngrok" url to which we should send the Zendesk Webhooks. 
* start the app standalone" `java -jar target/ZendeskToProfiles-1.0-SNAPSHOT-jar-with-dependencies.jar` or in the Intellij Debugger 
* update (or create) the Zendesk webhook that will send the webhook request ot our application.
* the program should start to get updates when Zendesk Tickets are updated.
* you can check on the status via the healthcheck report `https://localhost:5000/health`
## ToDo list
- [ ] Test the remaining functionality for SendSafely access and unpacking.
- [ ] Containerize the application (preparation for deployment in Dremio Core)
- [ ] Add additional functionality for queries.json (via dqdoctor)
- [ ] Add additional functionality for gc.log(s) (via gceasy)
- [ ] Add additional functionality for thread dumps (via fastthread)