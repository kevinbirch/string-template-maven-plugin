/*
 * The MIT License
 *
 * Copyright 2013 The WebGuys.
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
package com.webguys.maven.plugin.st;

import java.io.File;
import org.apache.maven.plugin.logging.Log;
import org.stringtemplate.v4.STGroup;

/**
 * Interface for a resource processing class.  Implementers must provide a empty argument constructor.
 *
 * @author thrykol
 */
public interface Processor {
	public void setResourceFile(File resourceFile);

	public void setOutputDirectory(File outputDirectory);

	public void setStringTemplateGroup(STGroup group);

	public void setLog(Log log);

	public void process();
}
