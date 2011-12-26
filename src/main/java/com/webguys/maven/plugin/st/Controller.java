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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.MojoExecutionException;
import org.stringtemplate.v4.ST;

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

    public void invoke(ST st) throws MojoExecutionException
    {
        try
        {
            Class controllerClass = Class.forName(this.className);
            Method method = this.getMethod(controllerClass);

            Object result = this.invoke(controllerClass, method);

            this.applyResults(st, result);
        }
        catch(ClassNotFoundException e)
        {
            throw new MojoExecutionException("Unable to invoke controller (class not found).", e);
        }
        catch(Exception e)
        {
            throw new MojoExecutionException(String.format("Unable to invoke controller: %s", e.getMessage()), e);
        }
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

    private Object invoke(Class controllerClass, Method method)
        throws InstantiationException, IllegalAccessException, InvocationTargetException
    {
        Object controller = null;
        if(!Modifier.isStatic(method.getModifiers()))
        {
            controller = controllerClass.newInstance();
        }

        return method.invoke(controller);
    }

    private void applyResults(ST st, Object result) throws MojoExecutionException
    {
        if(null == result)
        {
            throw new MojoExecutionException(String.format("The result invoking %s.%s was null.", this.className, this.method));
        }
        Map<Object, Object> attributes = (Map) result;

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
