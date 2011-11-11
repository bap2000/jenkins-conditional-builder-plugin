/*
 * The MIT License
 *
 * Copyright (C) 2011 by Anthony Robinson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkins_ci.plugins.conditional_builder;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.RunCondition;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class ConditionalBuilder extends Builder {

    public static final String PROMOTION_JOB_TYPE = "hudson.plugins.promoted_builds.PromotionProcess";

    private final RunCondition condition;
    private final BuildStep buildStep;
    private final BuildStepRunner runner;

    @DataBoundConstructor
    public ConditionalBuilder(final BuildStep buildStep, final RunCondition condition, final BuildStepRunner runner) {
        this.buildStep = buildStep;
        this.condition = condition;
        this.runner = runner;
    }

    public BuildStep getBuildStep() {
        return buildStep;
    }

    public RunCondition getCondition() {
        return condition;
    }

    public BuildStepRunner getRunner() {
        return runner;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public Collection getProjectActions(final AbstractProject<?, ?> project) {
        return buildStep.getProjectActions(project);
    }

    @Override
    public boolean prebuild(final AbstractBuild<?, ?> build, final BuildListener listener) {
        return runner.prebuild(condition, buildStep, build, listener);
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
                                                                                                throws InterruptedException, IOException {
        return runner.perform(condition, buildStep, build, launcher, listener);
    }

    @Extension(ordinal = Integer.MAX_VALUE - 500)
    public static class ConditionalBuilderDescriptor extends BuildStepDescriptor<Builder> {

        public static DescriptorExtensionList<BuilderDescriptorLister, Descriptor<BuilderDescriptorLister>>
                                                                                        getAllBuilderDescriptorListers() {
            return Hudson.getInstance().<BuilderDescriptorLister, Descriptor<BuilderDescriptorLister>>
                                                                                        getDescriptorList(BuilderDescriptorLister.class);
        }

        private BuilderDescriptorLister builderLister;

        @DataBoundConstructor
        public ConditionalBuilderDescriptor(final BuilderDescriptorLister builderLister) {
            this.builderLister = builderLister;
        }

        public ConditionalBuilderDescriptor() {
            load();
            if (builderLister == null)
                builderLister = new DefaultBuilderDescriptorLister();
        }

        public BuilderDescriptorLister getBuilderLister() {
            return builderLister;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            final ConditionalBuilderDescriptor newConfig = req.bindJSON(ConditionalBuilderDescriptor.class, json);
            if (newConfig.builderLister != null)
                builderLister = newConfig.builderLister;
            save();
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.builder_displayName();
        }

        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
            //@TODO enable for matrix builds - requires aggregation
//            return FreeStyleProject.class.equals(aClass);
            return !MatrixProject.class.equals(aClass) && !PROMOTION_JOB_TYPE.equals(aClass.getCanonicalName());
        }

        public DescriptorExtensionList<BuildStepRunner, BuildStepRunner.BuildStepRunnerDescriptor> getBuildStepRunners() {
            return BuildStepRunner.all();
        }

        public List<? extends Descriptor<? extends RunCondition>> getRunConditions() {
            return RunCondition.all();
        }

        public List<? extends Descriptor<? extends BuildStep>> getAllowedBuilders(AbstractProject<?,?> project) {
            if (project == null)
                project = Stapler.getCurrentRequest().findAncestorObject(AbstractProject.class);
            return builderLister.getAllowedBuilders(project);
        }

        public Object readResolve() {
            if (builderLister == null)
                builderLister = new DefaultBuilderDescriptorLister();
            return this;
        }

    }

}
