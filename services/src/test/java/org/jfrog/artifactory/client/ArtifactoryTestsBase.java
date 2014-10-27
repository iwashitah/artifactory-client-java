package org.jfrog.artifactory.client;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import static org.apache.commons.codec.binary.Base64.encodeBase64;
import static org.apache.commons.lang.StringUtils.remove;
import static org.jfrog.artifactory.client.ArtifactoryClient.create;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @author jbaruch
 * @since 30/07/12
 */
public abstract class ArtifactoryTestsBase {
    protected static final String NEW_LOCAL = "new-local";
    protected static final String PATH = "m/a/b/c.txt";
    protected static final String LIBS_RELEASE_LOCAL = "libs-release-local";
    protected static final String LIBS_RELEASE_VIRTUAL = "libs-release";
    protected static final String JCENTER = "jcenter";
    protected static final String JCENTER_CACHE = JCENTER + "-cache";
    protected static final String LIST_PATH = "api/repositories";
    private static final String CLIENTTESTS_ARTIFACTORY_ENV_VAR_PREFIX = "CLIENTTESTS_ARTIFACTORY_";
    private static final String CLIENTTESTS_ARTIFACTORY_PROPERTIES_PREFIX = "clienttests.artifactory.";
    protected Artifactory artifactory;
    protected String username;
    private String password;
    protected String url;
    protected String filePath;
    protected long fileSize;
    protected String fileMd5;
    protected String fileSha1;

    @BeforeClass
    public void init() throws IOException {

        Properties props = new Properties();
        // This file is not in GitHub. Create your own in src/test/resources.
        InputStream inputStream = this.getClass().getResourceAsStream("/artifactory-client.properties");
        if (inputStream != null) {
            props.load(inputStream);
        }

        url = readParam(props, "url");
        username = readParam(props, "username");
        password = readParam(props, "password");
        filePath = readParam(props, "filePath");
        fileSize = Long.valueOf(readParam(props, "fileSize"));
        fileMd5 = readParam(props, "fileMd5");
        fileSha1 = readParam(props, "fileSha1");

        artifactory = create(url, username, password);
    }

    private String readParam(Properties props, String paramName) {
        if (props.size() > 0) {
            return props.getProperty(CLIENTTESTS_ARTIFACTORY_PROPERTIES_PREFIX + paramName);
        }
        String paramValue = System.getProperty(CLIENTTESTS_ARTIFACTORY_PROPERTIES_PREFIX + paramName);
        if (paramValue == null) {
            paramValue = System.getenv(CLIENTTESTS_ARTIFACTORY_ENV_VAR_PREFIX + paramName.toUpperCase());
        }
        if (paramValue == null) {
            failInit();
        }
        return paramValue;
    }

    private void failInit() {
        fail(
                "Failed to load test Artifactory instance credentials." +
                        "Looking for System properties '" + CLIENTTESTS_ARTIFACTORY_PROPERTIES_PREFIX + "url', 'clienttests.artifactory.username' and 'clienttests.artifactory.password', " +
                        "or properties file with those properties in classpath," +
                        "or Environment variables '" + CLIENTTESTS_ARTIFACTORY_ENV_VAR_PREFIX + "URL', 'CLIENTTESTS_ARTIFACTORY_USERNAME' and 'CLIENTTESTS_ARTIFACTORY_PASSWORD'");
    }

    @AfterClass
    public void clean() {
        artifactory.close();
    }

    protected String curl(String path, String method) throws IOException {
        String authStringEnc = new String(encodeBase64((username + ":" + password).getBytes()));
        URLConnection urlConnection = new URL(url + "/" + path).openConnection();
        urlConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
        if (urlConnection instanceof HttpURLConnection) {
            ((HttpURLConnection) urlConnection).setRequestMethod(method);
        }
        try (InputStream is = urlConnection.getInputStream()) {
            return textFrom(is);
        }
    }

    protected String curl(String path) throws IOException {
        return curl(path, "GET");
    }

    protected String curlAndStrip(String path) throws IOException {
        return curlAndStrip(path, "GET");
    }

    protected String curlAndStrip(String path, String method) throws IOException {
        String result = curl(path, method);
        result = remove(result, ' ');
        result = remove(result, '\r');
        result = remove(result, '\n');
        return result;
    }

    protected String textFrom(InputStream is) throws IOException {
        try (InputStreamReader isr = new InputStreamReader(is)) {
            int numCharsRead;
            char[] charArray = new char[1024];
            StringBuilder sb = new StringBuilder();
            while ((numCharsRead = isr.read(charArray)) > 0) {
                sb.append(charArray, 0, numCharsRead);
            }
            return sb.toString();
        }
    }

    protected String deleteRepoIfExists(String repoName) throws IOException {
        try {
            String result = artifactory.repository(repoName).delete();
            assertTrue(result.startsWith("Repository " + repoName + " and all its content have been removed successfully."));
            assertFalse(curl(LIST_PATH).contains("\""+repoName+"\""));
            return result;
        } catch (Exception e) {
            if (e.getMessage().equals("Not Found")) { //if repo wasn't found - that's ok.
                return e.getMessage();
            } else {
                throw e;
            }
        }
    }
}
