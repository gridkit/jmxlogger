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

import java.io.StringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.gridkit.jmxlogger.JmxLoggerConfig.Config;
import org.junit.Assert;
import org.junit.Test;


public class JmxLoggerConfigTest {

	public static String PATTERNS =
		"USERNAME [a-zA-Z0-9_-]+\n" + 
		"USER %{USERNAME}\n" +
		"INT (?:[+-]?(?:[0-9]+))\n" +
		"BASE10NUM (?<![0-9.+-])(?>[+-]?(?:(?:[0-9]+(?:\\.[0-9]+)?)|(?:\\.[0-9]+)))\n" +
		"NUMBER (?:%{BASE10NUM})\n" +
		"BASE16NUM (?<![0-9A-Fa-f])(?:[+-]?(?:0x)?(?:[0-9A-Fa-f]+))\n" +
		"BASE16FLOAT \\b(?<![0-9A-Fa-f.])(?:[+-]?(?:0x)?(?:(?:[0-9A-Fa-f]+(?:\\.[0-9A-Fa-f]*)?)|(?:\\.[0-9A-Fa-f]+)))\\b\n" +
		"POSINT \\b(?:[1-9][0-9]*)\\b\n" +
		"NONNEGINT \\b(?:[0-9]+)\\b\n" +
		"WORD \\b\\w+\\b\n";			
	
	public static String CONFIG1 = 
		"<mbean-appender>\n" +
		"<patterns>\n" +
		"<![CDATA[\n" +
		PATTERNS +
		"]]>\n" + 
		"</patterns>\n" +
		"<match>\n" + 
		"<pattern>%{NUMBER:N}</pattern>\n" +
		"<var name=\"NN\">N</var>\n" + 
		"<mbean>\n" +
		"<name>CustomBean:id=%{NN}</name>\n" + 
		"<report>NN</report>\n" + 
		"</mbean>\n" +
		"</match>\n" +
		"</mbean-appender>";

	@Test
	public void test_unmarshal() throws JAXBException {
		JAXBContext ctx = JAXBContext.newInstance(Config.class);
		Config c = (Config) ctx.createUnmarshaller().unmarshal(new StringReader(CONFIG1));
		
		Assert.assertEquals(1, c.matchers.size());
		Assert.assertEquals(1, c.matchers.get(0).beans.size());
	}
	
}
