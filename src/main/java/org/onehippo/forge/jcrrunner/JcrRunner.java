/*
 *  Copyright 2009 Hippo.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.onehippo.forge.jcrrunner;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

/**
 * Main wrapper to start the shell.
 */
public final class JcrRunner {

    private static Runner runner = null;

    private static final String DEFAULT_CONFIG_FILE = "runner.properties";

    /**
     * Private constructor.
     */
    private JcrRunner() {
        super();
    }

    public static void main(final String[] args) throws IOException {
        // read & parse config
        String fileName = DEFAULT_CONFIG_FILE;
        if (args != null && args.length > 0) {
            fileName = args[0];
        }
        File file = new File(fileName);
        RunnerConfig config = new RunnerConfig(new BufferedInputStream(new FileInputStream(file)));

        // register hook for proper shutdown
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());

        JcrHelper.setServerUrl(config.getRepositoryUrl());
        JcrHelper.setUsername(config.getRepositoryUser());
        JcrHelper.setPassword(config.getRepositoryPass());
        JcrHelper.connect();

        try {
            // start the runner
            runner = new Runner();
            runner.registerPlugins(config.getPluginConfigs());
            runner.setPath(config.getRepositoryPath());
            runner.start();
        } catch (PathNotFoundException e) {
            System.err.println(e.getMessage());
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
        JcrHelper.disconnect();
    }

    /**
     * Trivial shutdown hook class.
     */
    static class ShutdownHook extends Thread {
        /**
         * Exit properly on shutdown.
         */
        public void run() {
            if (runner != null) {
                runner.stop();
            }
            JcrHelper.disconnect();
        }
    }
}
