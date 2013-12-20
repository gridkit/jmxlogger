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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.gridkit.jmxlogger.JmxLoggerConfig.Config;
import org.gridkit.jmxlogger.JmxLoggerConfig.MBean;
import org.gridkit.jmxlogger.JmxLoggerConfig.Matcher;
import org.gridkit.jmxlogger.JmxLoggerConfig.Variable;
import org.gridkit.jorka.Jorka;
import org.gridkit.jorka.Jorka.Match;

/**
 * This log appender parses structural data from log messages, calculates basic
 * statistical aggregates and exposes it as MBeans.
 * 
 * @author Alexey Ragozin (alexey.ragozin@gmail.com)
 */
public class StatisticsMBeanAppender extends AppenderSkeleton {

    // private static final Logger LOGGER =
  	// LogManager.getLogger(StatisticsMBeanAppender.class);
  
  	private static final int DEFAULT_BUCKET_LIMIT = 1000;
  	private static final int DEFAULT_BUFFER_SIZE = 512;
  	private static final long DEFAULT_TIME_DEPTH = TimeUnit.SECONDS.toMillis(30);

	private static final double S2M = TimeUnit.SECONDS.toMillis(1);

	private int defaultBufferSize = DEFAULT_BUFFER_SIZE;
	private long defaultTimeDepth = DEFAULT_TIME_DEPTH;
	private int bucketLimit = DEFAULT_BUCKET_LIMIT;

	private Map<ObjectName, StatsBucket> buckets = new LinkedHashMap<ObjectName, StatsBucket>();
	private AtomicLong bucketModCount = new AtomicLong();

	private Jorka patternLibrary = new Jorka();
	private Map<String, LineMatcher> matchers = new LinkedHashMap<String, LineMatcher>();

	private MBeanPublishTask publisher;

	private Logger errorLogger;

	private boolean tryinit;

	public synchronized TimerTask publishJmx(MBeanPublisher server) {
		if (publisher != null) {
			throw new IllegalStateException("MBeanServer is already connected");
		}
		publisher = new MBeanPublishTask(server);
		Timer timer = new Timer("Statistics JMX bean registrator", true);
		timer.schedule(publisher, 5000, 5000);
		return publisher;
	}

	@Override
	public synchronized void close() {
		publisher.cancel();
	}

	@Override
	public boolean requiresLayout() {
		return true;
	}

	public void setAutoRegister(boolean enabled) {
		if (enabled) {
			publishJmx(new PlatformMBeanPublisher());
		}
	}

	public void setBucketLimit(int bucketLimit) {
		this.bucketLimit = bucketLimit;
	}

	public void setConfig(String config) {
		try {
			InputStream is = getClass().getClassLoader().getResourceAsStream(
					config);
			if (is == null) {
				if (new File(config).isFile()) {
					is = new FileInputStream(config);
				}
			}
			if (is == null) {
				throw new IllegalArgumentException("Configuration not found");
			}
			JAXBContext ctx = JAXBContext.newInstance(Config.class);
			Config cfg = (Config) ctx.createUnmarshaller().unmarshal(is);
			processConfig(cfg);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (JAXBException e) {
			throw new RuntimeException(e);
		}
	}

	public void processConfig(Config cfg) {
	    for(String include : cfg.includes) {
	        addPatternsFromFile(include);
	    }
		for(String patterns : cfg.patterns) {
		    addPatterns(patterns);
		}
		for(Matcher matcher: cfg.matchers) {
		    String pattern = matcher.pattern.pattern;
		    if (matcher.pattern.type == null) {
		        pattern = Jorka.simpleTemplateToRegEx(pattern);
		    }
			Map<String, String> vars = new HashMap<String, String>();
			for(Variable var: matcher.vars) {
				var.name = var.name.trim();
				JmxLoggerConfig.validateVarName(var.name);
				if (var.name == null) {
					throw new IllegalArgumentException("@name is missing for <var>");
				}
				if (var.expr == null || var.expr.trim().length() == 0) {
					throw new IllegalArgumentException("Content is missing for <var>");
				}
				if (vars.containsKey(var.name)) {
					throw new IllegalArgumentException("Duplicate var '" + var.name + "'");
				}
				vars.put(var.name, var.expr);
			}
			
			for(MBean reporter: matcher.beans) {
				try {
					long td = reporter.timeDepth == null ? -1 : TimeIntervalParser.toMillis(reporter.timeDepth);
					addSimpleReporter(pattern, vars, reporter.mbean, reporter.valueRef, reporter.description, reporter.bufferSize, td);
				} catch (Exception e) {
					tryInitLogger();
					if (errorLogger != null) {
						errorLogger.error("Configuration error", e);
					}
					else {
						System.err.println("jmxlogger:ERROR Configuration error");
						e.printStackTrace();
					}
				}
			}
		}		
	}

	private void tryInitLogger() {
		Logger logger = LogManager.getLogger(getClass());
		if (logger.getAllAppenders().hasMoreElements()) {
			errorLogger = logger;
		}
	}

	public void addPatterns(String patterns) {
		try {
            this.patternLibrary.addPatternFromReader(new StringReader(patterns));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
	}

	public void addPatternsFromFile(String path) {
	    try {
	        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
	        if (is == null) {
	            is = getClass().getClassLoader().getResourceAsStream(path);
	        }
	        if (is == null) {
	            File f = new File(path);
	            if (f.isFile()) {
	                is = new FileInputStream(f);
	            }
	        }
	        if (is == null) {
	            throw new RuntimeException("Unable to find in resources or file system: " + path);
	        }
	        patternLibrary.addPatternFromReader(new InputStreamReader(is));
	    } catch (IOException e) {
	        throw new RuntimeException(e);
	    }
	}

	/**
	 * Exposed mostly for testing reasons
	 */
	public void addSimpleReporter(String pattern, Map<String, String> variables, String beanName, String expression, String description, int bufferSize, long timeDepth) {
	    LineMatcher m;
		if (matchers.containsKey(pattern)) {
			m = matchers.get(pattern);
		} else {
			LineMatcher mm = new LineMatcher();
			Jorka j = patternLibrary.copyPatterns();
			j.compile(pattern);
			mm.rootPattern = pattern;
			mm.jorka = j;
			matchers.put(pattern, mm);
			m = mm;
		}

		Reporter rep = new Reporter();
		JmxLoggerConfig.validateMBeanName(beanName, variables.keySet());
		if (!variables.containsKey(expression)) {
			throw new IllegalArgumentException("Bad repport expression: "
					+ expression);
		}
		initVars(rep, variables);
		rep.expression = expression;
		rep.description = description;
		rep.mbean = beanName;
		rep.bufferSize = bufferSize < 0 ? defaultBufferSize : bufferSize;
		rep.timeDepth = timeDepth < 0 ? defaultTimeDepth : timeDepth;

		m.repoters.add(rep);
	}

	private void initVars(Reporter rep, Map<String, String> variables) {
		for (String key : variables.keySet()) {
			String value = variables.get(key).trim();
			if (value.length() > 0 && Character.isJavaIdentifierStart(value.charAt(0))) {
				rep.fields.put(key, value.split("[.]"));
			} else {
				rep.consts.put(key, value);
			}
		}
	}

	public void setDefaultBufferSize(int bufferSize) {
		this.defaultBufferSize = bufferSize;
	}

	public void setDefaultTimeDepth(String depth) {
		this.defaultTimeDepth = TimeUnit.MILLISECONDS
				.toNanos(TimeIntervalParser.toMillis(depth));
	}

	@Override
	protected void append(LoggingEvent event) {
		if (tryinit) {
			tryinit = false;
			tryInitLogger();
		}
		String text = getLayout().format(event);
		processLogLine(event.getTimeStamp(), text);
	}

	public void processLogLine(long timestamp, String line) {
		for (LineMatcher matcher : matchers.values()) {
			if (matcher.repoters.isEmpty()) {
				continue;
			}
			Match m = matcher.jorka.match(line);
			if (m != null) {
				m.parse();
				Map<String, Object> tree = m.toMap();
				for (Reporter rep : matcher.repoters) {
					try {
						reportTree(timestamp, rep, tree);
					} catch (Exception e) {
						if (isErrorLogEnabled()) {
							logError("Reporing error for line: " + line, e);
						}
					}
				}
			}
		}
	}

	private void reportTree(long timestamp, Reporter rep,
			Map<String, Object> tree) {
		Map<String, String> state = new HashMap<String, String>();
		state.putAll(rep.consts);
		for (String key : rep.fields.keySet()) {
			state.put(key, resolve(tree, rep.fields.get(key)));
		}
		ObjectName name = JmxLoggerConfig
				.instantiateMBeanName(rep.mbean, state);
		if (name == null) {
			if (isErrorLogEnabled()) {
				logError("Failed instantiate MBane name. [" + rep.mbean + "] "
						+ state);
			}
			;
		} else {
			String val = state.get(rep.expression);
			double v = Double.parseDouble(val);
			StatsBucket bucket = ensureBucket(name, rep.description,
					rep.bufferSize, rep.timeDepth);
			bucket.append(timestamp, v);
		}
	}

	private String resolve(Map<String, Object> tree, String[] path) {
		Object c = tree;
		for (String f : path) {
			c = ((Map<?, ?>) c).get(f);
			if (c == null) {
				return "";
			}
		}

		return (String) c;
	}

	private synchronized StatsBucket ensureBucket(ObjectName on,
			String description, int bufferSize, long timeDepth) {

		StatsBucket b = buckets.remove(on);
		if (b == null) {
			if (buckets.size() >= bucketLimit) {
				Iterator<Entry<ObjectName, StatsBucket>> it = buckets
						.entrySet().iterator();
				it.next();
				it.remove();
			}

			b = new StatsBucket(on, description, bufferSize, timeDepth);
			bucketModCount.incrementAndGet();
		}
		buckets.put(b.bucketName, b);

		return b;
	}

	private boolean isErrorLogEnabled() {
		return errorLogger != null && errorLogger.isDebugEnabled();
	}

	private void logError(String msg) {
		if (errorLogger != null) {
			errorLogger.debug(msg);
		}
	}

	private void logError(String msg, Exception e) {
		if (errorLogger != null) {
			errorLogger.debug(msg, e);
		}
	}

	class MBeanPublishTask extends TimerTask implements Runnable {

		private Map<ObjectName, StatsBucket> registered = new HashMap<ObjectName, StatsBucket>();
		private MBeanPublisher publisher;
		private long lastModCount = 0;

		public MBeanPublishTask(MBeanPublisher publisher) {
			this.publisher = publisher;
		}

		@Override
		public synchronized void run() {
			if (bucketModCount.get() != lastModCount) {
				synchronized (StatisticsMBeanAppender.this) {
					lastModCount = bucketModCount.get();
					for (ObjectName name : buckets.keySet()) {
						if ((!registered.containsKey(name))
								|| (registered.get(name) != buckets.get(name))) {
							if (registered.remove(name) != null) {
								unregisterMBean(name);
							}
							StatsBucket bucket = buckets.get(name);
							registered.put(name, bucket);
							registerMBean(name, new Stats(bucket));
						}
					}
					if (registered.size() > bucketLimit) {
						List<ObjectName> deadBeans = new ArrayList<ObjectName>();
						for (ObjectName name : registered.keySet()) {
							if (!buckets.containsKey(name)) {
								deadBeans.add(name);
							}
						}
						for (ObjectName name : deadBeans) {
							unregisterMBean(name);
							registered.remove(name);
						}
					}
				}
			}
		}

		@Override
		public synchronized boolean cancel() {
			for (ObjectName name : registered.keySet()) {
				unregisterMBean(name);
			}
			registered.clear();
			return super.cancel();
		}

		private void registerMBean(ObjectName name, Stats statProxy) {
			try {
				publisher.registerMBean(name, statProxy);
			} catch (Exception e) {
				if (isErrorLogEnabled()) {
					logError("Failed to register: " + name, e);
				}
			}
		}

		private void unregisterMBean(ObjectName name) {
			try {
				publisher.unregisterMBean(name);
			} catch (Exception e) {
				if (isErrorLogEnabled()) {
					logError("Failed to unregister: " + name, e);
				}
			}
		}
	}

	static class LineMatcher {

		Jorka jorka;
		String rootPattern;

		List<Reporter> repoters = new ArrayList<Reporter>();

	}

	static class Reporter {

		String mbean;
		Map<String, String[]> fields = new HashMap<String, String[]>();
		Map<String, String> consts = new HashMap<String, String>();
		String expression;
		String description;

		int bufferSize = -1;
		long timeDepth = -1;

	}

	static class StatsBucket {

		private ObjectName bucketName;
		private String description;

		private long[] timestamps;
		private double[] samples;
		private long timeDepth;

		private long anchorTimestamp;
		private long totalCount;
		private double runningSum;
		private double runningSquareSum;
		private double runningCubeSum;
		private BigDecimal totalSum = BigDecimal.valueOf(0);
		private BigDecimal totalSquareSum = BigDecimal.valueOf(0);
		private BigDecimal totalCubeSum = BigDecimal.valueOf(0);
		private double totalMin = Double.NaN;
		private double totalMax = Double.NaN;

		private int head = 0;
		private int tail = 0;
		private long lastTimestamp;

		public StatsBucket(ObjectName name, String description, int bufSize,
				long timeDepth) {
			bucketName = name;
			this.description = description;
			timestamps = new long[bufSize];
			samples = new double[bufSize];
			lastTimestamp = anchorTimestamp = System.currentTimeMillis();
			this.timeDepth = timeDepth;
		}

		public synchronized void append(long timestamp, double sample) {
			++totalCount;
			runningSum += sample;
			runningSquareSum += sample * sample;
			runningCubeSum += sample * sample * sample;
			if (totalCount % 1000 == 0) {
				flushRunning();
			}
			totalMax = Double.isNaN(totalMax) ? sample : Math.max(totalMax,
					sample);
			totalMin = Double.isNaN(totalMin) ? sample : Math.min(totalMin,
					sample);
			lastTimestamp = timestamps[tail] = Math.max(lastTimestamp,
					timestamp);
			samples[tail] = sample;
			tail = inc(tail);
			if (tail == head) {
				head = inc(head);
			}
		}

		private void flushRunning() {
			totalSum = totalSum.add(BigDecimal.valueOf(runningSum));
			totalSquareSum = totalSquareSum.add(BigDecimal
					.valueOf(runningSquareSum));
			totalCubeSum = totalCubeSum.add(BigDecimal.valueOf(runningCubeSum));
			runningSum = 0;
			runningSquareSum = 0;
			runningCubeSum = 0;
		}

		public synchronized InstantStats analyze() {

			flushRunning();

			InstantStats stats = new InstantStats();

			long startTime = Long.MIN_VALUE;
			long nowTime = System.currentTimeMillis();
			long cutTime = nowTime - timeDepth;
			int count = 0;
			double total = 0;
			double min = Double.NaN;
			double max = Double.NaN;

			int n = head;
			while (n != tail) {
				if (timestamps[n] > cutTime) {
					if (startTime == Long.MIN_VALUE) {
						startTime = timestamps[n];
					}
					++count;
					total += samples[n];
					if (Double.isNaN(max) || max < samples[n]) {
						max = samples[n];
					}
					if (Double.isNaN(min) || min > samples[n]) {
						min = samples[n];
					}
				} else {
					head = inc(head);
				}
				n = inc(n);
			}

			if (count > 0) {
				stats.count = count;
				stats.min = min;
				stats.max = max;
				stats.avg = total / count;
				stats.window = (nowTime - startTime) / S2M;
			}

			if (count > 2) {
				stats.rate = S2M * count / (nowTime - startTime);
				double sqTotal = 0;
				n = head;
				while (n != tail) {
					if (timestamps[n] > cutTime) {
						double dv = stats.avg - samples[n];
						sqTotal += dv * dv;
					}
					n = inc(n);
				}
				stats.stdDev = Math.sqrt(sqTotal / count);
			}

			stats.tsAnchor = anchorTimestamp;
			stats.totalCount = totalCount;
			stats.totalMin = totalMin;
			stats.totalMax = totalMax;
			stats.totalSum = totalSum;
			stats.totalSquareSum = totalSquareSum;
			stats.totalCubeSum = totalCubeSum;

			stats.description = description;
			stats.timestamp = System.currentTimeMillis();

			return stats;
		}

		private int inc(int idx) {
			return (idx + 1) % timestamps.length;
		}
	}

	public static class InstantStats {

		public String description;

		public double count = 0;
		public double avg = Double.NaN;
		public double stdDev = Double.NaN;
		public double min = Double.NaN;
		public double max = Double.NaN;
		public double rate = Double.NaN;
		public double window = Double.NaN;

		public long tsAnchor;
		public long timestamp;
		public long totalCount;
		public BigDecimal totalSum;
		public BigDecimal totalSquareSum;
		public BigDecimal totalCubeSum;
		public double totalMin;
		public double totalMax;
	}
}
