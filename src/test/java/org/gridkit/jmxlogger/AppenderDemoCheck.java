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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

public class AppenderDemoCheck {

	@Test
	public void show_beans() throws InterruptedException {
		
		StatisticsMBeanAppender appender = new StatisticsMBeanAppender();
		appender.setPatternLibrary(JmxLoggerConfigTest.PATTERNS);
		
		Map<String, String> vars = new HashMap<String, String>();
		vars.put("NAME", "NAME");
		vars.put("TIME", "TIME");
		
		appender.setBucketLimit(10);
		appender.addSimpleReporter("%{WORD:NAME}: %{NUMBER:TIME}ms", vars, "TestBean:name=%{NAME}", "TIME", "", -1, -1);
		appender.setAutoRegister(true);
		
		Random rnd = new Random();
		for(int i = 0; i != 20000; ++i) {
			Thread.sleep(10);
			for(int j = 0; j != 100; ++j) {
				appender.processLogLine(System.currentTimeMillis(), "X" + (i / 100)  + ": " + rnd.nextGaussian() + "ms");
			}
		}
		
		appender.close();		
	}
	
}
