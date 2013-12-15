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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigDecimal;

import javax.management.DescriptorKey;

public interface StatsMBean {

	@Description("Timestamp of sample (in local epoch time)")
	public long getTimestamp();
		
	@Description("Counter description")
	public String getDescription();
	
	@Description("Number of recent events")
	public double getSlidingCount();
	
	@Description("Average value for recent events")
	public double getSlidingAverage();
	
	@Description("Max value for recent events")
	public double getSlidingMin();

	@Description("Max value for recent events")
	public double getSlidingMax();
	
	@Description("Std.dev. of value for recent events")
	public double getSlidingStdDev();
	
	@Description("Recent event rate (events/second)")
	public double getSlidingRate();
	
	@Description("Aggregation time window")
	public double getSlidingWindow();

	@Description("Absolute time (in ms) when MBean have started collecting statistics (in local epoch time)")
	public long getLifetimeStart();

	@Description("Total count during MBean lifetime")
	public long getLifetimeCount();

	@Description("Total sum during MBean lifetime")
	public BigDecimal getLifetimeSum();

	@Description("Total sum of squares during MBean lifetime")
	public BigDecimal getLifetimeSquareSum();

	@Description("Total sum of cubes during MBean lifetime")
	public BigDecimal getLifetimeCubeSum();

	@Description("Minimum seen value during MBean lifetime")
	public double getLifetimeMin();

	@Description("Maximum seen value during MBean lifetime")
	public double getLifetimeMax();

	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@interface Description {
		
		@DescriptorKey("Description")
		String value();
		
	}
}
