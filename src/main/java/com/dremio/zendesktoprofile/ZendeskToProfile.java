package com.dremio.zendesktoprofile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendsafely.Package;
import com.sendsafely.SendSafely;
import com.sendsafely.exceptions.DownloadFileException;
import com.sendsafely.exceptions.PackageInformationFailedException;
import com.sendsafely.exceptions.PasswordRequiredException;
import io.javalin.Javalin;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Comment;
import org.zendesk.client.v2.model.Ticket;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ZendeskToProfile {
    static final String SENDSAFELY_KEY = System.getenv("SENDSAFELY_API_KEY");
    static final String SENDSAFELY_SECRET = System.getenv("SENDSAFELY_API_SECRET");
    static final String ZENDESK_USER = System.getenv("ZENDESK_USER");
    static final String ZENDESK_TOKEN = System.getenv("ZENDESK_TOKEN");
    private static final String SENDSAFELY_URL = System.getenv("SENDSAFELY_URL");
    private static final String ZENDESK_URL = System.getenv("ZENDESK_URL");
    // let's get the current working directory.
    private static final String DIRECTORY = System.getProperty("user.dir") + "/zendesktoprofiles_temp_files/";
    private static final String MESSAGE = "This ticket includes a secure attachment. Use this link to access the attached files:";
    private static final String MESSAGE_2 = "Package ID#";

    private static final String BASE_URL = System.getenv("BASE_URL");

    // exact view name from Zendesk:
    private static final Logger LOG = LogManager.getLogger(ZendeskToProfile.class);

    public static void main(String... argv) throws Exception {

        LOG.debug("hello. starting: {}", new Date());

        ZendeskToProfile main = new ZendeskToProfile();
        Zendesk zenDesk = new Zendesk.Builder(ZENDESK_URL).setUsername(ZENDESK_USER).setToken(ZENDESK_TOKEN).build();
        SendSafely sendSafely = new SendSafely(SENDSAFELY_URL, SENDSAFELY_KEY, SENDSAFELY_SECRET);

        main.run(zenDesk, sendSafely);
        System.exit(10);  // should never finish, so this is a bad exit code.

    }

    private void run(Zendesk zenDesk, SendSafely sendSafely) {

        Javalin app = Javalin.create().start(5000);

        //add routes and methods.
        app.get("/liveness", ctx -> ctx.result("Hello World."));
        app.post("/zendesk/events", ctx -> {
            process(zenDesk, sendSafely, ctx.body());
            ctx.status(201);
        });

        app.get("/health", ctx -> {
            ctx.result(health());
            ctx.status(200);
        });

        app.get("/readyness", ctx -> ctx.result("ready."));
        while (true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                LOG.error("uh-oh. exception: " + ex.getMessage());
                ex.printStackTrace();
                System.exit(111);
            }
        }
    }

    private String health() {
        String ans = "";
        try {
            ObjectMapper mapper = new ObjectMapper();
            ans = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(HealthCheck.getInstance());
        } catch (JsonProcessingException ex) {
            LOG.error("exception: {}", ex.getMessage(), ex);
            System.exit(111);
        }
        return ans;
    }

    private void process(Zendesk zd, SendSafely sendSafely, String incomingJSONString) {

        HealthCheck.getInstance().setLastWebhookEpoch();
        // parse incoming payload:
        JSONObject obj = new JSONObject(incomingJSONString);
        long ticketId = obj.getLong("id");
        String theText = obj.getString("comment");

        Ticket ticket = zd.getTicket(ticketId);
        LOG.info("{}: {}", ticket.getId(), ticket.getSubject());

        // this list is the package ids.
        List<String> packageIds = parseComment(theText);

        if (packageIds.size() == 0) {
            LOG.debug("{}: no packages in comment {} ", ticket.getId(), theText);
            return;
        }

        // there is a bug in the SendSafely APIs in which the "Private Key" is not always returned
        // when you access the package info, by the Id (eg.  ABCD-EFGH)
        // here we have the package id's. We need to look for a ticket comment (in HTML format)
        // that contains the packages, so we can get the URL for the package.
        List<String> URLs = getPackageLinks(zd, ticketId, packageIds);

        // this is the comment we may (or may not) attach to the ticket.
        StringBuilder ticketUpdateHTMLText = new StringBuilder();

        // here we have a list of the links for the SendSafely packages.
        // loop through the packages - look for interesting files....
        for (String u : URLs) {
            LOG.info("processing: {}", u);
            HealthCheck.getInstance().incrementSendSafelyPackagesProcessed();

            // wipe contents of our destination directory (we'll do this for each package):
            try {
                FileUtils.cleanDirectory(new java.io.File(DIRECTORY));
            } catch (IOException ex) {
                LOG.error("exception cleaning directory: {}", ex.getMessage(), ex);
                System.exit(45);
            }

            Package pkg = null;
            try {
                pkg = sendSafely.getPackageInformationFromLink(u);

            } catch (PackageInformationFailedException ex) {
                LOG.error("Exception for URL: {} {}", u, ex.getMessage(), ex);
                System.exit(47);
            }

            // check each file in the SendSafely package.
            for (com.sendsafely.File file : pkg.getFiles()) {
                HealthCheck.getInstance().incrementFilesChecked();

                //TODO: debugging - delete me
                LOG.info("{}: checking file name {}  size: {}", ticket.getId(), file.getFileName(), file.getFileSize());

                if (file.getFileName().contains("gc.log") || file.getFileName().startsWith("server.gc")) {
                    java.io.File newFile = downloadFromSendSafely(sendSafely, pkg, file);

                    String[] commandWithArgs = null;
                    try {
                        commandWithArgs = new String[]{"dqdoctor", "gc-logs", newFile.getCanonicalPath()};
                    } catch (IOException ex) {
                        LOG.error("Failed to getCanonicalPath(): {}" + ex.getMessage(), ex);
                        System.exit(19);
                    }

                    RunCommand rc = new RunCommand(commandWithArgs);
                    LOG.info("{}:  gc.log dqDoctor summary {}", ticket.getId(), rc.getStdout());

                    // should we update the ticket?


                } else if (file.getFileName().contains("queries.json")) {
                    // use dqDoctor queries-json.
                    LOG.info("{}: not processing queries.json. {}", ticket.getId(), file.getFileName());

                } else if (file.getFileName().contains(".zip")) {

                    // download the zip file.
                    File newFile = downloadFromSendSafely(sendSafely, pkg, file);
                    boolean headers = false;
                    boolean profiles = false;

                    ZipFileList zfl = new ZipFileList(newFile);
                    // check if this zip file contains headers.json AND profile*.json
                    for (String filename : zfl.getList()) {
                        if (filename.contains("header.json")) {
                            headers = true;

                        } else if (filename.contains("profile_attempt")) {
                            profiles = true;
                        }
                        LOG.info("{}: downloaded {}", ticket.getId(), filename);

                        // looks like a query profile?
                        if (headers && profiles) {
                            headers = false;  // reset
                            profiles = false; // reset

                            // upload the zip file to profiles.drem.io
                            // empty reply String in case of failure.
                            String profileLink = sendToProfiles(newFile);
                            if (StringUtils.isEmpty(profileLink)) {
                                continue;
                            }

                            // add the filename and the link
                            ticketUpdateHTMLText.append("File name: ");
                            ticketUpdateHTMLText.append(newFile.getName());
                            ticketUpdateHTMLText.append("<br>");
                            ticketUpdateHTMLText.append(profileLink);
                            ticketUpdateHTMLText.append("<br>");

                            // we would like to include metadata from headers.json
                            try {
                                String[] unzipCommand = new String[]{"unzip", newFile.getCanonicalPath(), "-d", DIRECTORY};

                                // unzip it.
                                RunCommand rc = new RunCommand(unzipCommand);
                                if (rc.getStderr().length() > 0) {
                                    // this is a problem.
                                    LOG.error("{} unzip command failed: {}", ticket.getId(), rc.getStderr());
                                    continue;   // next file
                                }
                                HealthCheck.getInstance().incrementProfilesUploaded();
                                HealthCheck.getInstance().setLastProfilesUploadEpoch();

                                String temp = FileUtils.readFileToString(new File(DIRECTORY + "header.json"), StandardCharsets.UTF_8);

                                // parse it.
                                JSONObject json = new JSONObject(temp);
                                JSONObject x = json.getJSONObject("job");
                                JSONObject y = x.getJSONObject("info");
                                long startTime = y.getLong("startTime");
                                long finishTime = y.getLong("finishTime");
                                JSONObject z = y.getJSONObject("jobId");
                                String jobId = (String) z.get("id");

                                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                                // reformat the Date objects to be much nicer looking...
                                ticketUpdateHTMLText.append(String.format("JobId: %s StartTime: %s FinishTime: %s<br>", jobId, sdf2.format(new Date(startTime)), sdf2.format(new Date(finishTime))));
                                ticketUpdateHTMLText.append("<br>");

                            } catch (JSONException | IOException ex) {
                                LOG.info("JSONException | IOException: {}" + ex.getMessage(), ex);
                            }

                            // run dqDoctor on the profile_attempt*.json
//                                    String[] djDoctorCommand = {"dqdoctor", "profile-json", DIRECTORY + profileFileName};
//                                    String ans = runCommand(djDoctorCommand, true).replace("\n", "<br>");
//                                    ticketUpdateHTMLText.append(ans);

                        }
                    }
                }
            }
        }

        if (ticketUpdateHTMLText.toString().length() > 0) {
            Comment c = new Comment();
            c.setPublic(false);
            c.setHtmlBody(ticketUpdateHTMLText.toString());
            ticket.setComment(c);

            // TODO: debugging.  remember to enable this for production.
//            zd.updateTicket(ticket);

            LOG.info("ticket {} was updated content: {}", ticket.getId(), ticketUpdateHTMLText);
        } else {
            LOG.info("{}: done. Did not add an entry to the ticket.", ticket.getId());
        }
    }

    /**
     * parse ticket comments until we find the urls that match the packageIds, or we run out of comments.
     *
     * @param zd       Zendesk API object
     * @param ticketId ticketid we're going to work with.
     * @param ids      list of package id's (eg. ABCD-EFGH)
     * @return - list of URLs for the corresponding package ids.
     */
    List<String> getPackageLinks(Zendesk zd, long ticketId, List<String> ids) {
        List<String> URLs = new ArrayList<>();

        for (Comment c : zd.getRequestComments(ticketId)) {
            if (ids.size() == URLs.size()) {
                break;
            }

            // not a public comment...
            if (!c.isPublic()) {
                continue;
            }

            Document doc = Jsoup.parse(c.getHtmlBody());
            Elements links = doc.select("a[href]");     // anchor tag with "href"
            for (Element e : links) {
                if (e.attr("abs:href").contains("sendsafely.dremio.com/receive")) {
                    for (String i : ids) {
                        if (e.attr("abs:href").contains(i)) {
                            URLs.add(e.attr("abs:href"));
                        }
                    }
                }
            }
        }
        return URLs;
    }

    private List<String> parseComment(String theText) {
        List<String> packages = new ArrayList<>();

        String[] lines = theText.split("\n");  // break into lines.
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].equals(MESSAGE)) {
                // look ahead to the next line.
                if (lines[i + 1].contains(MESSAGE_2)) {
                    String token = lines[i + 1].replace(MESSAGE_2, "").trim();
                    packages.add(token);
                }
            }
        }
        return packages;
    }

    /**
     * Download a file from the SendSafely package.
     */
    File downloadFromSendSafely(SendSafely sendSafely, Package pkg, com.sendsafely.File file) {
        // download files in the package
        java.io.File newFile = null;
        try {
            // NOTE: SendSafely, by default, downloads the file to /tmp, and gives the
            // file a unique suffix (which is quite annoying).
            java.io.File tempFile = sendSafely.downloadFile(pkg.getPackageId(), file.getFileId(), pkg.getKeyCode());

            // create a new file name with the customer's originally specified name
            newFile = new File(DIRECTORY + file.getFileName());

            // just check if there were duplicates in the sendSafely package
            // (the packages are created by humans, and apparently duplicate files can happen)
            if (newFile.exists()) {
                newFile.delete();
            }

            // move the file and rename it.
            FileUtils.moveFile(tempFile, newFile);

        } catch (IOException | DownloadFileException | PasswordRequiredException ex) {
            LOG.error("Exception downloading from SendSafely: {}" + ex.getMessage(), ex);
            System.exit(63);
        }

        LOG.info("Downloaded File: {}  length {}", newFile.getName(), newFile.length());
        return newFile;
    }

    /**
     * Today, we're just going to `curl` this puppy up to profile.drem.io
     *
     * @param file - name of the file to upload
     * @return - the link to access this profile.
     */
    String sendToProfiles(File file) {
        String[] command = {"curl", "-F", String.format("profile=@%s", file.getPath()), BASE_URL + "upload"};
        String ans = "";

        RunCommand rc = new RunCommand(command);

        try {
            // create json object from curl response
            JSONObject obj = new JSONObject(rc.getStdout());
            JSONArray arr = obj.getJSONArray("submissionIds");
            for (int i = 0; i < arr.length(); i++) {
                String link = BASE_URL + "/submission/" + arr.get(i).toString();
                ans += "Link to profile:  <a href=\"" + link + "\">" + link + "</a><br>";
            }
        } catch (JSONException ex) {
            LOG.error("stdout: \"{}\"", rc.getStdout());
            LOG.error("JSONException: {}", ex.getMessage(), ex);
        }

        return ans;
    }
}
