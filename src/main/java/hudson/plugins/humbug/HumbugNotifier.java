package hudson.plugins.humbug;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HumbugNotifier extends Notifier {

    private Humbug humbug;
    private String stream;
    private String hudsonUrl;
    private boolean smartNotify;

    // getters for project configuration..
    // Configured stream name should be null unless different from descriptor/global values
    public String getConfiguredStreamName() {
        if ( DESCRIPTOR.getStream().equals(stream) ) {
            return null;
        } else {
            return stream;
        }
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private static final Logger LOGGER = Logger.getLogger(HumbugNotifier.class.getName());

    public HumbugNotifier() {
        super();
        initialize();
    }

    public HumbugNotifier(String email, String apiKey, String subdomain, String stream, String hudsonUrl, boolean smartNotify) {
        super();
        initialize(email, apiKey, subdomain, stream, hudsonUrl, smartNotify);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    private void publish(AbstractBuild<?, ?> build) throws IOException {
        checkHumbugConnection();
        Result result = build.getResult();
        String changeString = "";
        if (!build.hasChangeSetComputed()) {
            changeString = "Could not determine changes since last build.";
        } else if (build.getChangeSet().iterator().hasNext()) {
            ChangeLogSet changeSet = build.getChangeSet();
            ChangeLogSet.Entry entry = build.getChangeSet().iterator().next();
            if (!build.getChangeSet().isEmptySet()) {
                // If there seems to be a commit message at all, try to list all the changes.
                changeString = "Changes since last build:\n";
                for (ChangeLogSet.Entry e: build.getChangeSet()) {
                    String commitMsg = e.getMsg().trim();
                    if (commitMsg.length() > 47) {
                        commitMsg = commitMsg.substring(0, 46)  + "...";
                    }
                    String author = e.getAuthor().getDisplayName();
                    String id = e.getCommitId().substring(0,8);
                    changeString += "\n* `"+ author + "` " + commitMsg;
                }
            }
        }
        String resultString = result.toString();
        if (!smartNotify && result == Result.SUCCESS) resultString = resultString.toLowerCase();

        String message = "Build " + build.getDisplayName();
        if (hudsonUrl != null && hudsonUrl.length() > 1 && (smartNotify || result != Result.SUCCESS)) {
            message = "[" + message + "](" + hudsonUrl + build.getUrl() + ")";
        }
        message += ": ";
        message += "**" + resultString + "**";
        if (changeString.length() > 0 ) {
            message += "\n\n";
            message += changeString;
        }
        humbug.sendStreamMessage(stream, build.getProject().getName(), message);
    }

    private String getCommitHash(String changeLogPath) throws IOException {
        String sha = "";
        BufferedReader reader = new BufferedReader(new FileReader(changeLogPath));
        String line;
        while((line = reader.readLine()) != null) {
            if (line.matches("^commit [a-zA-Z0-9]+$")) {
                sha = line.replace("commit ", "");
                break;
            }
        }
        reader.close();
        return sha;
    }

    private void checkHumbugConnection() {
        if (humbug == null) {
            initialize();
        }
    }

    private void initialize()  {
        initialize(DESCRIPTOR.getEmail(), DESCRIPTOR.getApiKey(), DESCRIPTOR.getSubdomain(), stream, DESCRIPTOR.getHudsonUrl(), DESCRIPTOR.getSmartNotify());
    }

    private void initialize(String email, String apiKey, String subdomain, String streamName, String hudsonUrl, boolean smartNotify) {
        humbug = new Humbug(email, apiKey, subdomain);
        this.stream = streamName;
        this.hudsonUrl = hudsonUrl;
        this.smartNotify = smartNotify;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        // If SmartNotify is enabled, only notify if:
        //  (1) there was no previous build, or
        //  (2) the current build did not succeed, or
        //  (3) the previous build failed and the current build succeeded.
        if (smartNotify) {
            AbstractBuild previousBuild = build.getPreviousBuild();
            if (previousBuild == null ||
                build.getResult() != Result.SUCCESS ||
                previousBuild.getResult() != Result.SUCCESS)
            {
                publish(build);
            }
        } else {
            publish(build);
        }
        return true;
    }
}
