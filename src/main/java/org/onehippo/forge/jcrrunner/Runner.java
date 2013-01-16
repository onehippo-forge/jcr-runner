/*
 *  Copyright 2009-2013 Hippo B.V. (http://www.onehippo.com)
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

import org.hippoecm.repository.api.HippoNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Runner {

    private static final Logger log = LoggerFactory.getLogger(Runner.class);
    private String query;
    private String queryLanguage;
    private String startPath;
    private List<String> pathElements;
    private int level = 0;
    private int wildcardLevel = -1;

    private volatile boolean keepRunning = true;

    private List<RunnerPlugin> plugins = new ArrayList<RunnerPlugin>();

    //-------------------------------- QUERY -----------------------------//
    public void setQuery(String query) {
        this.query = query;
    }

    public void setQueryLanguage(String queryLanguage) {
        this.queryLanguage = queryLanguage;
    }

    //-------------------------------- PATH PARSING -----------------------------//
    public void setPath(String path) throws RepositoryException {
        if (path == null || path.length() == 0) {
            return;
        }
        String absPath = makePathAbsolute(path);
        startPath = findStartPath(absPath);
        pathElements = Arrays.asList(absPath.substring(1).split("/"));
        level = startPath.split("/").length - 2;
        wildcardLevel = pathElements.indexOf("**");
    }

    private String findStartPath(String absPath) {
        String beginPath;
        int starPos = absPath.indexOf('*');
        if (starPos > 0) {
            beginPath = absPath.substring(0, starPos);
        } else {
            beginPath = absPath;
        }
        int pos = beginPath.lastIndexOf('/');
        if (pos > -1) {
            beginPath = beginPath.substring(0, pos);
        }
        return beginPath;
    }

    private String makePathAbsolute(String path) {
        if (path == null || path.length() == 0) {
            return "/";
        } else if (!path.startsWith("/")) {
            return "/" + path;
        } else {
            return path;
        }
    }

    /**
     * Match:
     * - /asdf/** 
     * - /asdf/asd*sdf
     * - /asdf/qwer
     * @param path
     * @return
     */
    private boolean matchNodePath(String path) {
        if (wildcardLevel != -1 && level >= wildcardLevel) {
            return true;
        }
        if (level >= pathElements.size()) {
            return false;
        }
        String element = pathElements.get(level);
        if (element.equals("*")) {
            return true;
        }
        if (element.equals(path)) {
            return true;
        }
        int pos = element.indexOf('*');
        if (pos > 0) {
            String prefix = element.substring(0, pos);
            String suffix = "";
            if ((pos + 1) < element.length()) {
                suffix = element.substring(pos + 1);
            }
            if (path.startsWith(prefix) && path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the node is virtual. 
     * @param node
     * @return true if the node is virtual otherwise false
     */
    private boolean isVirtual(Node node) {
        if (node == null) {
            return false;
        }
        if (!(node instanceof HippoNode)) {
            return false;
        }
        HippoNode hippoNode = (HippoNode) node;
        try {
            Node canonical = hippoNode.getCanonicalNode();
            if (canonical == null) {
                return true;
            }
            return !hippoNode.getCanonicalNode().isSame(hippoNode);
        } catch (RepositoryException e) {
            log.warn("Error while trying to determine if the node is virtual: " + node.getClass().getName()
                    + ". This usually happens with semi-virtual nodes, returning true: " + e.getMessage());
            return true;
        }
    }

    //------------------------------- VISITOR ------------------------?
    private void recursiveVisit(String path) throws RepositoryException {
        Node node;
        try {
            node = JcrHelper.getNode(path);
        } catch (PathNotFoundException e) {
            log.info("Path not found: " + path);
            return;
        }
        visit(node);
        JcrHelper.refresh(true);
        try {
            node = JcrHelper.getNode(path);
        } catch (PathNotFoundException e) {
            log.info("Path not found: " + path);
            return;
        }
        if (node.hasNodes()) {
            NodeIterator iter = node.getNodes();
            while (iter.hasNext() && keepRunning) {
                Node child = iter.nextNode();
                if (child != null && !isVirtual(child)) {
                    level++;
                    try {
                        String name = child.getName();
                        if (matchNodePath(name)) {
                            recursiveVisit(child.getPath());
                        }
                    } catch (InvalidItemStateException e) {
                        log.warn("InvalidItemStateException while getting child node, the node will be skipped: "
                                + e.getMessage());
                    }
                    level--;
                }
            }
        }
    }

    private void runPathVisitor() throws RepositoryException {
        if (startPath == null) {
            log.info("No path set. Skipping path visitor.");
            return;
        }
        Node root = JcrHelper.getNode(startPath);
        String rootPath = root.getPath();
        visitStart(root);
        recursiveVisit(rootPath);
        if (keepRunning) {
            JcrHelper.refresh(true);
            visitEnd(JcrHelper.getNode(startPath));
        }
    }

    private void runQueryVisitor() throws RepositoryException {
        if (query == null) {
            log.info("No query set. Skipping query visitor.");
            return;
        }
        Session session = JcrHelper.getRootNode().getSession();

        QueryManager queryManager = session.getWorkspace().getQueryManager();
        Query jcrQuery = queryManager.createQuery(query, queryLanguage);
        QueryResult results = jcrQuery.execute();

        NodeIterator iter = results.getNodes();

        boolean isFirst = true;
        while (iter.hasNext() && keepRunning) {
            Node child = iter.nextNode();
            if (child != null && !isVirtual(child)) {
                if (isFirst) {
                    isFirst = false;
                    visitStart(child);
                    visit(child);
                } else if (!iter.hasNext()) {
                    visit(child);
                    visitEnd(child);
                } else {
                    visit(child);
                }
                JcrHelper.refresh(true);
            }
        }
    }

    //------------------------------- LIFECYCLE -----------------------//
    public Runner() {
    }

    public void start() {
        log.info("Runner starting.");
        initPlugins();
        keepRunning = true;
        try {
            runPathVisitor();
            runQueryVisitor();
        } catch (PathNotFoundException e) {
            log.error("Path not found: " + startPath);
            destroyPlugins();
        } catch (RepositoryException e) {
            log.error("Error while running the plugins", e);
            destroyPlugins();
        }
        log.info("Runner finished.");
    }

    public void stop() {
        log.debug("Runner stopping.");
        keepRunning = false;
        destroyPlugins();
        log.info("Runner stopped.");
    }

    // ------------------------------- PLUGIN INTERFACES -----------------//
    private void visit(Node node) {
        synchronized (plugins) {
            for (RunnerPlugin plugin : plugins) {
                plugin.visit(node);
            }
        }
    }

    private void visitStart(Node node) {
        synchronized (plugins) {
            for (RunnerPlugin plugin : plugins) {
                plugin.visitStart(node);
            }
        }
    }

    private void visitEnd(Node node) {
        synchronized (plugins) {
            for (RunnerPlugin plugin : plugins) {
                plugin.visitEnd(node);
            }
        }
    }

    public void registerPlugin(RunnerPlugin plugin) {
        synchronized (plugins) {
            log.debug("Registering plugin: " + plugin.getClass().getName());
            plugins.add(plugin);
        }
    }

    public void initPlugins() {
        synchronized (plugins) {
            for (RunnerPlugin plugin : plugins) {
                log.debug("Initializing plugin: " + plugin.getClass().getName());
                plugin.init();
            }
        }
    }

    public void destroyPlugins() {
        synchronized (plugins) {
            for (RunnerPlugin plugin : plugins) {
                log.debug("Destroying plugin: " + plugin.getClass().getName());
                plugin.destroy();
            }
        }
    }

    public void registerPlugins(List<RunnerPluginConfig> pluginConfigs) {
        RunnerPlugin runnerPlugin = null;
        for (RunnerPluginConfig pluginConfig : pluginConfigs) {
            switch (pluginConfig.getType()) {
            case JAVA:
                runnerPlugin = RunnerPluginFactory.createJavaPlugin(pluginConfig);
                break;
            case BEANSHELL:
                runnerPlugin = RunnerPluginFactory.createBeanShellPlugin(pluginConfig);
                break;
            default:
                log.error("Unknown plugin of type {}", pluginConfig.getType());
            }
            if (runnerPlugin != null) {
                registerPlugin(runnerPlugin);
            }
        }
    }

}
