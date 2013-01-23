/*
 * The MIT License
 *
 * Copyright (c) 2011 Kevin Birch <kmb@pobox.com>. Some rights reserved.
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

import org.apache.maven.ProjectDependenciesResolver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.stringtemplate.v4.ST;

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
     * The static properties to be provided to the controller.
     *
     * @parameter
     */
    private Map<String, String> properties;

    /**
     * Should the this controller attempt to be compiled?
     *
     * @parameter default-value="true"
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
     * @parameter default-value="3.0"
     */
    private String compilerVersion = "3.0";

    private Object controllerInstance = null;

    public void invoke(ST st, ExecutionEnvironment executionEnvironment, ProjectDependenciesResolver dependenciesResolver, Log log) throws MojoExecutionException
    {
        try
        {
            Class controllerClass = this.findControllerClass(dependenciesResolver, executionEnvironment, log);
            Method method = this.getMethod(controllerClass);

            this.applyProperties(controllerClass, this.properties, log);
            Object results = this.invoke(controllerClass, method, log);

            this.applyResults(st, results);
        }
        catch(Exception e)
        {
            throw new MojoExecutionException(String.format("Unable to invoke controller: %s (%s)", this.className, e.getMessage()), e);
        }
    }

    private Class findControllerClass(ProjectDependenciesResolver dependenciesResolver, ExecutionEnvironment executionEnvironment, Log log)
        throws MojoExecutionException, ClassNotFoundException, MalformedURLException, ArtifactResolutionException, ArtifactNotFoundException
    {
        try
        {
            return this.loadController(executionEnvironment.getMavenProject(), executionEnvironment.getMavenSession(), dependenciesResolver);
        }
        catch(ClassNotFoundException e)
        {
            if(this.compile)
            {
                log.info(String.format("Unable to find the class: %s.  Attempting to compile it...", this.className));
                return this.compileAndLoadController(log, dependenciesResolver, executionEnvironment);
            }
            else
            {
                throw new MojoExecutionException(String.format("The class %s is not in the classpath, and compilation is not enabled.", this.className), e);
            }
        }
    }

    private Class compileAndLoadController(Log log, ProjectDependenciesResolver dependenciesResolver, ExecutionEnvironment executionEnvironment)
        throws MojoExecutionException, ClassNotFoundException, MalformedURLException, ArtifactResolutionException, ArtifactNotFoundException
    {
        MavenProject project = executionEnvironment.getMavenProject();

        Set<Artifact> originalArtifacts = this.configureArtifacts(project);
        this.executeCompilerPlugin(executionEnvironment, log);
        Class result = this.loadController(project, executionEnvironment.getMavenSession(), dependenciesResolver);
        project.setArtifacts(originalArtifacts);
        return result;
    }

    private Set<Artifact> configureArtifacts(MavenProject project)
    {
        Set<Artifact> originalArtifacts = project.getArtifacts();
        project.setArtifacts(project.getDependencyArtifacts());
        return originalArtifacts;
    }

    private void executeCompilerPlugin(ExecutionEnvironment executionEnvironment, Log log) throws MojoExecutionException
    {
        String path = this.className.replace(".", File.separator) + ".java";
        log.info(String.format("Compiling %s...", path));

        executeMojo(
            plugin(
                groupId("org.apache.maven.plugins"),
                artifactId("maven-compiler-plugin"),
                version(compilerVersion)
            ),
            goal("compile"),
            configuration(
                element(name("source"), sourceVersion),
                element(name("target"), targetVersion),
                element(name("includes"), element("include", path))
            ),
            executionEnvironment
        );
    }

    private Class loadController(MavenProject project, MavenSession session, ProjectDependenciesResolver dependenciesResolver)
        throws MalformedURLException, ClassNotFoundException, ArtifactResolutionException, ArtifactNotFoundException
    {
        ArrayList<String> scopes = new ArrayList<String>(1);
        scopes.add(JavaScopes.RUNTIME);
        Set<Artifact> artifacts = dependenciesResolver.resolve(project, scopes, session);

        ArrayList<URL> urls = new ArrayList<URL>();
        urls.add(new File(project.getBuild().getOutputDirectory()).getAbsoluteFile().toURI().toURL());
        for(Artifact artifact : artifacts)
        {
            urls.add(artifact.getFile().getAbsoluteFile().toURI().toURL());
        }

        ClassLoader loader = URLClassLoader.newInstance(urls.toArray(new URL[urls.size()]), this.getClass().getClassLoader());
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

    private void applyProperties(Class controllerClass, Map<String, String> properties, Log log)
            throws IllegalAccessException, InvocationTargetException, InstantiationException
    {
        if(null == properties || properties.isEmpty())
        {
            return;
        }

        Method setProperties = null;
        try
        {
            setProperties = controllerClass.getMethod("setProperties", Map.class);
        }
        catch (NoSuchMethodException ignored)
        {
            // ignore
        }
        if(null != setProperties)
        {
            this.invoke(controllerClass, setProperties, log, properties);
        }
    }

    private Object invoke(Class controllerClass, Method method, Log log, Object ... args)
        throws InstantiationException, IllegalAccessException, InvocationTargetException
    {
        Object controller = null;
        if(!Modifier.isStatic(method.getModifiers()))
        {
            if (null == this.controllerInstance)
            {
                this.controllerInstance = controllerClass.newInstance();
            }
            controller = this.controllerInstance;
        }

        log.info(String.format("Invoking controller method: %s.%s()", controllerClass.getName(), method.getName()));
        return method.invoke(controller, args);
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
