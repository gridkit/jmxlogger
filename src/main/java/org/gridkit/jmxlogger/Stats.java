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

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import org.gridkit.jmxlogger.StatisticsMBeanAppender.InstantStats;
import org.gridkit.jmxlogger.StatisticsMBeanAppender.StatsBucket;

class Stats implements StatsMBean  {
	
	private StatsBucket source;
	private long lastTimestamp;
	private InstantStats stats;
	
	public Stats(StatsBucket source) {
		this.source = source;
	}
	
	
	@Override
	public String getDescription() {
		return getStats().description;
	}
	
	@Override
	public long getTimestamp() {
		return getStats().timestamp;
	}	
	
	@Override
	public double getSlidingCount() {
		return getStats().count;
	}
	
	@Override
	public double getSlidingAverage() {
		return getStats().avg;
	}
	
	@Override
	public double getSlidingMin() {
		return getStats().min;
	}

	@Override
	public double getSlidingMax() {
		return getStats().max;
	}
	
	@Override
	public double getSlidingStdDev() {
		return getStats().stdDev;
	}
	
	@Override
	public double getSlidingRate() {
		return getStats().rate;
	}
	
	@Override
	public double getSlidingWindow() {
		return getStats().window;
	}
	
	@Override
	public long getLifetimeStart() {
		return getStats().tsAnchor;
	}

	@Override
	public long getLifetimeCount() {
		return getStats().totalCount;
	}

	@Override
	public BigDecimal getLifetimeSum() {
		return getStats().totalSum;
	}

	@Override
	public BigDecimal getLifetimeSquareSum() {
		return getStats().totalSquareSum;
	}

	@Override
	public BigDecimal getLifetimeCubeSum() {
		return getStats().totalCubeSum;
	}
	
	@Override
	public double getLifetimeMin() {
		return getStats().totalMin;
	}

	@Override
	public double getLifetimeMax() {
		return getStats().totalMax;
	}

	private synchronized InstantStats getStats() {
		if (stats != null && ((System.nanoTime() - lastTimestamp) < TimeUnit.MILLISECONDS.toNanos(100))) {
			return stats;
		}
		else {
			stats = source.analyze();
			return stats;
		}
	}
}