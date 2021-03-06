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
package org.onehippo.forge.jcrrunner.plugins;

import javax.jcr.Session;

import org.onehippo.forge.jcrrunner.RunnerPlugin;
import org.onehippo.forge.jcrrunner.RunnerPluginConfig;

public abstract class AbstractRunnerPlugin implements RunnerPlugin {

    /**
     * Holder for the current plugin id
     */
    private String id;

    /**
     * Holder for the current plugin's config
     */
    private RunnerPluginConfig config;

    @Override
    public void init(Session session) {
        // empty default implementation
    }

    @Override
    public void destroy(Session session) {
        // empty default implementation
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public final void setId(String id) {
        this.id = id;
    }

    @Override
    public final void setConfig(RunnerPluginConfig config) {
        this.config = config;
    }

    @Override
    public final String getConfigValue(String key) {
        return config.getValue(key);
    }

    @Override
    public final String getConfigValue(String key, String defaultValue) {
        if (config.getValue(key) == null) {
            return defaultValue;
        }
        return config.getValue(key);
    }

    @Override
    public final long getLongConfigValue(String key, long defaultValue) {
        final String value = getConfigValue(key);
        if (value == null || "".equals(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public final int getIntConfigValue(String key, int defaultValue) {
        final String value = getConfigValue(key);
        if (value == null || "".equals(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public final boolean getBooleanConfigValue(String key, boolean defaultValue) {
        final String value = getConfigValue(key);
        if (value == null || "".equals(value)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
