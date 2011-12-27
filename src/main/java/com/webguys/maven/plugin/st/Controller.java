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
 */

package com.webguys.maven.plugin.st;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.stringtemplate.v4.ST;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

public class Controller
{
    /**
     * The name of the class to instantiate.
     *
     * @parameter
     * @required
     */
    private String className;

    /**
     * The name of the method to invoke.
     *
     * @parameter
     * @required
     */
    private String method;

    /**
     * Should the this controller attempt to be compiled?
     *
     * @parameter
     */
    private boolean compile = true;

    /**
     * @parameter default-value="1.6"
     */
    private String sourceVersion = "1.6";

    /**
     * @parameter default-value="1.6"
     */
    private String targetVersion = "1.6";

    /**
     * @parameter default-value="2.3.2"
     */
    private String compilerVersion = "2.3.2";

    public void invoke(ST st, ExecutionEnvironment executionEnvironment, Log log) throws MojoExecutionException
    {
        try
        {
            Class controllerClass = this.findControllerClass(log, executionEnvironment);
            Method method = this.getMethod(controllerClass);

            Object result = this.invoke(controllerClass, method, log);

            this.applyResults(st, result);
        }
        catch(Exception e)
        {
            throw new MojoExecutionException(String.format("Unable to invoke controller: %s (%s)", this.className, e.getMessage()), e);
        }
    }

    private Class findControllerClass(Log log, ExecutionEnvironment executionEnvironment)
        throws MojoExecutionException, ClassNotFoundException, MalformedURLException, DependencyResolutionRequiredException
    {
        try
        {
            MavenProject project = executionEnvironment.getMavenProject();

            Set<Artifact> originalArtifacts = this.configureArtifacts(project, log);
            return this.loadController(project, originalArtifacts, log);
        }
        catch(ClassNotFoundException e)
        {
            if(this.compile)
            {
                log.info(String.format("Unable to find the class: %s.  Attempting to compile it...", this.className));
                return this.compileAndLoadController(log, executionEnvironment);
            }
            else
            {
                throw new MojoExecutionException(String.format("The class %s is not in the classpath, and compilation is not enabled.", this.className), e);
            }
        }
    }

    private Class compileAndLoadController(Log log, ExecutionEnvironment executionEnvironment)
        throws MojoExecutionException, ClassNotFoundException, MalformedURLException, DependencyResolutionRequiredException
    {
        MavenProject project = executionEnvironment.getMavenProject();

        Set<Artifact> originalArtifacts = this.configureArtifacts(project, log);
        this.executeCompilerPlugin(executionEnvironment, log);
        return this.loadController(project, originalArtifacts, log);
    }

    private Set<Artifact> configureArtifacts(MavenProject project, Log log)
    {
        log.info("Configuring classpath...");
        Set<Artifact> originalArtifacts = project.getArtifacts();
        project.setArtifacts(project.getDependencyArtifacts());
        return originalArtifacts;
    }

    private void executeCompilerPlugin(ExecutionEnvironment executionEnvironment, Log log) throws MojoExecutionException
    {
        String path = this.className.replace(".", File.separator) + ".java";
        log.info(String.format("Adding %s to compiler include list...", path));

        log.info("Compiling...");
        executeMojo(
            plugin(
                groupId("org.apache.maven.plugins"),
                artifactId("maven-compiler-plugin"),
                version(compilerVersion)
            ),
            goal("compile"),
            configuration(
                element(name("verbose"), "true"),
                element(name("source"), sourceVersion),
                element(name("target"), targetVersion),
                element(name("includes"), element("include", path))
            ),
            executionEnvironment
        );
    }

    private Class loadController(MavenProject project, Set<Artifact> originalArtifacts, Log log)
        throws DependencyResolutionRequiredException, MalformedURLException, ClassNotFoundException
    {
        log.info("Loading controller class file...");
        ArrayList<URL> urls = new ArrayList<URL>();
        for(Object element : project.getRuntimeClasspathElements())
        {
            urls.add(new File((String)element).getAbsoluteFile().toURI().toURL());
        }
        ClassLoader loader = URLClassLoader.newInstance(urls.toArray(new URL[urls.size()]), this.getClass().getClassLoader());

        log.info("Resetting classpath...");
        project.setArtifacts(originalArtifacts);

        return loader.loadClass(this.className);
    }

    private Method getMethod(Class controllerClass) throws NoSuchMethodException, MojoExecutionException
    {
        Method method = controllerClass.getMethod(this.method);

        if(!method.getReturnType().isAssignableFrom(Map.class))
        {
            throw new MojoExecutionException(String.format("The return type of the method %s was not of type Map<String, Object>", this.method));
        }
        
        return method;
    }

    private Object invoke(Class controllerClass, Method method, Log log)
        throws InstantiationException, IllegalAccessException, InvocationTargetException
    {
        Object controller = null;
        if(!Modifier.isStatic(method.getModifiers()))
        {
            controller = controllerClass.newInstance();
        }

        log.info(String.format("Invoking controller: %s.%s()", controllerClass.getName(), method.getName()));
        return method.invoke(controller);
    }

    private void applyResults(ST st, Object result) throws MojoExecutionException
    {
        if(null == result)
        {
            throw new MojoExecutionException(String.format("The result invoking %s.%s was null.", this.className, this.method));
        }
        Map<Object, Object> attributes = (Map<Object, Object>) result;

        for(Entry<Object, Object> entry : attributes.entrySet())
        {
            Object key = entry.getKey();
            if(!(key instanceof String))
            {
                String msg = String.format("A non-String key of type %s was found in the %s.%s results.", key.getClass().getName(), this.className, this.method);
                throw new MojoExecutionException(msg);
            }
            st.add((String)key, entry.getValue());
        }
    }
}
