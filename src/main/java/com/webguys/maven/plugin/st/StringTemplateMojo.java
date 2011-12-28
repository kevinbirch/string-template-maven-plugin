/*
 * The MIT License
 *
 * Copyright (c) 2011 Kevin Birch <kevin.birch@gmail.com>. Some rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Created: 11/20/11 10:47 PM
 */

package com.webguys.maven.plugin.st;

import java.io.File;
import java.util.List;

import org.apache.maven.ProjectDependenciesResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupDir;
import org.stringtemplate.v4.misc.ErrorBuffer;
import org.twdata.maven.mojoexecutor.MojoExecutor.ExecutionEnvironment;

import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;

/**
 * Executes string template using a given controller.
 *
 * @goal render
 */
public class StringTemplateMojo extends AbstractMojo
{
    /**
     * The Maven Project Object
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The Maven Session Object
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;

    /**
     * The Maven PluginManager Object
     *
     * @component
     * @required
     */
    private BuildPluginManager pluginManager;

    /**
     * The Maven ProjectDependenciesResolver Object
     *
     * @component
     * @required
     */
    private ProjectDependenciesResolver dependenciesResolver;

    /**
     * The collection of templates to render.
     * @parameter
     * @required
     */
    private List<Template> templates;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        for(Template template : this.templates)
        {
            File templateDirectory = this.getTemplateDirectory(template);

            STGroup group = new STGroupDir(templateDirectory.getAbsolutePath());
            ErrorBuffer errorBuffer = new ErrorBuffer();
            group.setListener(errorBuffer);
            ST st = group.getInstanceOf(template.getName());

            if(null == st || !errorBuffer.errors.isEmpty())
            {
                throw new MojoExecutionException(String.format("Unable to execute template. %n%s", errorBuffer.toString()));
            }

            ExecutionEnvironment executionEnvironment = executionEnvironment(this.project, this.session, this.pluginManager);
            template.invokeController(st, executionEnvironment, this.dependenciesResolver, this.getLog());
            template.installProperties(st);
            template.render(st, this.project, this.getLog());
        }
    }

    private File getTemplateDirectory(Template template)
    {
        File templateDirectory = template.getDirectory();
        if(!templateDirectory.isAbsolute())
        {
            templateDirectory = new File(this.project.getBasedir(), templateDirectory.getPath());
        }

        return templateDirectory;
    }
}
