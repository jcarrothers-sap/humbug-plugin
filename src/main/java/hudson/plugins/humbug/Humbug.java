package hudson.plugins.humbug;

import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Humbug {
    private String url;
    private String email;
    private String apiKey;
    private static final Logger LOGGER = Logger.getLogger(Humbug.class.getName());

    public Humbug(String url, String email, String apiKey) {
        super();
        if (url.length() > 0 && !url.endsWith("/") ) {
            url = url + "/";
        }
        this.url = url;
        this.email = email;
        this.apiKey = apiKey;
    }

    protected HttpClient getClient() throws MalformedURLException {
      HttpClient client = new HttpClient();
      // TODO: It would be nice if this version number read from the Maven XML file
      // (which is possible, but annoying)
      // http://stackoverflow.com/questions/8829147/maven-version-number-in-java-file
      client.getParams().setParameter("http.useragent", "ZulipJenkins/0.1.2");
      URL urlObj = new URL(url);
      Proxy proxy = Jenkins.getInstance().proxy.createProxy(urlObj.getHost());
      if (proxy.type() != Proxy.Type.DIRECT) {
          InetSocketAddress addr = (InetSocketAddress)proxy.address();
          client.getHostConfiguration().setProxy(addr.getHostName(), addr.getPort());
      }
      return client;
    }

    protected String getApiEndpoint() {
        if (this.url.length() > 0) {
            return this.url + "api/v1/";
        }
        return "https://api.zulip.com/v1/";
    }

    public String getApiKey() {
      return this.apiKey;
    }

    public String getEmail() {
        return this.email;
    }

    public String post(String method, NameValuePair[] parameters) throws IOException {
        PostMethod post = new PostMethod(getApiEndpoint() + method);
        post.setRequestHeader("Content-Type", post.FORM_URL_ENCODED_CONTENT_TYPE);
        String auth_info = this.getEmail() + ":" + this.getApiKey();
        String encoded_auth = new String(Base64.encodeBase64(auth_info.getBytes()));
        post.setRequestHeader("Authorization", "Basic " + encoded_auth);

        try {
            post.setRequestBody(parameters);
            HttpClient client = getClient();

            client.executeMethod(post);
            String response = post.getResponseBodyAsString();
            if (post.getStatusCode() != HttpStatus.SC_OK) {
                String params = "";
                for (NameValuePair pair: parameters) {
                    params += "\n" + pair.getName() + ":" + pair.getValue();
                }
                LOGGER.log(Level.SEVERE, "Error sending Zulip message:\n" + response + "\n\n" +
                                         "We sent:" + params);
            }
            return response;
        } finally {
            post.releaseConnection();
        }
    }

//    public String get(String url) {
//        GetMethod get = new GetMethod("https://" + getHost() + "/api/v1/" + url);
//        get.setFollowRedirects(true);
//        get.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
//        try {
//            getClient().executeMethod(get);
//            verify(get.getStatusCode());
//            return get.getResponseBodyAsString();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        } finally {
//            get.releaseConnection();
//        }
//    }
//
//    public boolean verify(int returnCode) {
//        if (returnCode != 200) {
//            throw new RuntimeException("Unexpected response code: " + Integer.toString(returnCode));
//        }
//        return true;
//    }

    public String sendStreamMessage(String stream, String subject, String message) throws IOException {
        NameValuePair[] body = {new NameValuePair("api-key", this.getApiKey()),
                                new NameValuePair("email",   this.getEmail()),
                                new NameValuePair("type",    "stream"),
                                new NameValuePair("to",      stream),
                                new NameValuePair("subject", subject),
                                new NameValuePair("content", message)};
        return post("messages", body);
    }
}
