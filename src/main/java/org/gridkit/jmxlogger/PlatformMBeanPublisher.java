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

import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

/**
 * 
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public class PlatformMBeanPublisher implements MBeanPublisher {

	@Override
	public void registerMBean(ObjectName name, Object bean) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
		ManagementFactory.getPlatformMBeanServer().registerMBean(bean, name);
	}

	@Override
	public void unregisterMBean(ObjectName name) throws MBeanRegistrationException, InstanceNotFoundException {
		ManagementFactory.getPlatformMBeanServer().unregisterMBean(name);
	}
}
