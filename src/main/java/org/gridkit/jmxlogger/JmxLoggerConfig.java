/**
 * Copyright 2013 Alexey Ragozin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridkit.jmxlogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

class JmxLoggerConfig {

	public static void validateVarName(String name) {
		name = name.trim();
		if (name.length() == 0) {
			throw new IllegalArgumentException("var is empty");
		}
		else {
			if (!Character.isJavaIdentifierStart(name.charAt(0))) {
				throw new IllegalArgumentException("Not a valid name '" + name + "'");
			}
			for(int i = 1; i != name.length(); ++i) {
				if (!Character.isJavaIdentifierPart(name.charAt(i))) {
					throw new IllegalArgumentException("Not a valid name '" + name + "'");
				}
			}
		}
	}
	
	public static void validateMBeanName(String name, Collection<String> vars) {
		String nname = name;
		for(String var: vars) {
			nname = nname.replace((CharSequence) "%{" + var + "}" , "_" + var + "_");
		}
		try {
			new ObjectName(nname);
		} catch (MalformedObjectNameException e) {
			throw new IllegalArgumentException("Not a valid MBean name [" + name + "] (expanded: " + nname + ")");
		}
	}

	public static ObjectName instantiateMBeanName(String pattern, Map<String, String> vars) {
		String nname = pattern;
		for(String var: vars.keySet()) {
			nname = nname.replace((CharSequence) "%{" + var + "}" , vars.get(var));
		}
		try {
			return new ObjectName(nname);
		} catch (MalformedObjectNameException e) {
			return null;
		}
	}
	
	@XmlRootElement(name = "mbean-appender")	
	public static class Config {

		@XmlElement(name = "patterns")
		public String patterns;
		
		@XmlElement(name = "match")
		public List<Matcher> matchers = new ArrayList<Matcher>(); 
		
	}
	
	public static class Matcher {
		
		@XmlElement(name = "pattern")
		public String pattern;

		@XmlElement(name = "var")
		public List<Variable> vars = new ArrayList<Variable>(); 

		@XmlElement(name = "mbean")
		public List<MBean> beans = new ArrayList<MBean>(); 		
	}
	
	public static class Variable {
		
		@XmlAttribute(name = "name", required = true)
		public String name;
		
		@XmlValue
		public String expr;		
	}
	
	public static class MBean {
		
		@XmlElement(name = "name", required = true)
		public String mbean;
		
		@XmlElement(name = "report", required = true)
		public String valueRef;

		@XmlElement(name = "description")
		public String description;

		@XmlElement(name = "buffer-size")
		public int bufferSize = -1;

		@XmlElement(name = "time-depth")
		public String timeDepth = null;
		
	}	
}
