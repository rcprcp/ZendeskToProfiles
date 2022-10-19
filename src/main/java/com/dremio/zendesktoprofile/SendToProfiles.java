package com.dremio.zendesktoprofile;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;

import java.io.File;
import java.io.IOException;

public class SendToProfiles {
    private String zipFileName = "";
    private final String PROFILES_DREM_IO = "http://profiles.drem.io/api/upload";
//    private String PROFILES_DREM_IO = "http://httpbin.org/post";

    public SendToProfiles(String zipFileName) {
        this.zipFileName = zipFileName;
    }
/**
 * NOT USED AS WE'RE SENDING THE PROFILE VIA CURL
 */
    /**
     * send to profiles.drem.io
     *
     * @return url from which we can browse the results, "" if there was an error.
     */
    String sendIt() {
        try {
            HttpClient httpclient = HttpClients.createDefault();
            HttpPost httppost = new HttpPost(PROFILES_DREM_IO);

// Request parameters and other properties.
            httppost.setHeader("Accept", "*/*");
            httppost.setHeader("Accept-Encoding", "gzip, deflate");
            httppost.setHeader("Accept-Language", "en-US,en;q=0.9");
            // httppost.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36");
            httppost.setHeader("Content-type", "application/zip");
            //  httppost.setHeader("Content-Disposition", "form-data; name=\"profile\"; filename=\"" + zipFileName + "\"");

            HttpEntity entity = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.EXTENDED)
                    .addBinaryBody(zipFileName, new File(zipFileName))
                    .build();
            httppost.setEntity(entity);

            HttpResponse response = httpclient.execute(httppost);
            System.out.println(response.getCode() + " "
                    + response.getReasonPhrase());

            if (response.getCode() < 200 || response.getCode() > 201) {
                System.out.println("bad ´status:" + response.getCode());
            } else {
                // read returned info:  {"submissionIds":["fd8c38c6-a8ed-4ed8-9018-ac0429582ba8"]}%
            }

        } catch (IOException ex) {
            System.out.println(ex);
            ex.printStackTrace();
            System.exit(8);
        }
        return "";
    }
}
