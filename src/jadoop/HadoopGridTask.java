// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is part of Jadoop
// Copyright (c) 2016 Grant Braught. All rights reserved.
// 
// Jadoop is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published
// by the Free Software Foundation, either version 3 of the License,
// or (at your option) any later version.
// 
// Jadoop is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
// See the GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public
// License along with Jadoop.
// If not, see <http://www.gnu.org/licenses/>.
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

package jadoop;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a command to be run on a Hadoop Cluster. A HadoopGridTask must be
 * added to a HadoopGridJob to be run. The command in a HaddopGridTask will be
 * run using a Runtime.exec() call in the map method of a Hadoop Mapper object.
 * The command will be given a limited amount of time to execute before it is
 * timed out.
 * 
 * Any files required to execute the command can be distributed to the cluster
 * nodes using the HadoopGridJob. If the command requires multiple steps or
 * additional configuration (e.g. environment variables), the recommended
 * approach is to create a shell script for the task, distribute it via the
 * HadoopGridJob and then execute the script as the command.
 * 
 * HadoopGridTasks have several flags indicating their current state:
 * <UL>
 * <LI>started: true if the task has been started on the cluster.
 * <LI>running: true if the task is currently running on the cluster.
 * <LI>finished: true if the task was started and is no longer running.
 * <LI>terminated: true if the task is finished but did not finish on its own
 * (i.e. the job it was a part of was terminated or timed out).
 * <LI>timedout: true if the task was terminated because it did not complete
 * before the job it was a part of exceeded the time limit.
 * <LI>successful: true if the task completed with an exit code of 0.
 * </UL>
 * 
 * @see HadoopGridJob
 * 
 * @author Grant Braught
 * @author Xiang Wei
 * @author Dickinson College
 * @version Jun 17, 2015
 */
public class HadoopGridTask {
	private String key;
	private String[] cmd;
	private String stdOutput;
	private String stdError;
	private boolean captureStdOut;
	private boolean captureStdErr;
	private long timeout; // ms

	private boolean started;
	private boolean finished;
	private boolean timedout;
	private boolean terminated;

	private byte exitValue;

	/**
	 * Construct a new HadoopGridTask that captures standard output and standard
	 * error.
	 * 
	 * @param key
	 *            a key that can be used to retrieve the results after the
	 *            HadoopGridJob that is used to run has completed.
	 * @param command
	 *            the command line to be executed on the Hadoop cluster. Be sure
	 *            to include quotes around arguments that contain spaces.
	 * @param timeout
	 *            the timeout for the task in ms. The command run in the Mapper
	 *            will be terminated after this amount of time and the hadoop
	 *            task will terminate with a non-zero exit code. Long.MAX_VALUE
	 *            can be used if no practical time out is desired.
	 */
	public HadoopGridTask(String key, String command, long timeout) {
		this(key, command, true, true, timeout);
	}

	/**
	 * Construct a new HadoopGridTask.
	 * 
	 * @param key
	 *            a key that can be used to retrieve the results after the
	 *            HadoopGridJob that is used to run has completed.
	 * @param command
	 *            the command line to be executed on the Hadoop cluster. Be sure
	 *            to include quotes around arguments that contain spaces.
	 * @param captureStdOut
	 *            true to capture any output to standard output generated by the
	 *            execution of the command.
	 * @param captureStdErr
	 *            true to capture any output to standard error generated by the
	 *            execution of the command.
	 * @param timeout
	 *            the timeout for the task in ms. The command run in the Mapper
	 *            will be terminated after this amount of time and the hadoop
	 *            task will terminate with a non-zero exit code. Long.MAX_VALUE
	 *            can be used if no practical time out is desired.
	 */
	public HadoopGridTask(String key, String command, boolean captureStdOut,
			boolean captureStdErr, long timeout) {

		this.key = key;

		// getting rid of the quotes in command
		List<String> list = new ArrayList<String>();
		Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(command);
		while (m.find()) {
			String nextArg = m.group(1);
			nextArg = nextArg.replace("\"", "");
			list.add(nextArg);
		}
		cmd = new String[list.size()];
		list.toArray(cmd);

		stdOutput = "";
		stdError = "";
		this.captureStdOut = captureStdOut;
		this.captureStdErr = captureStdErr;
		this.timeout = timeout;

		started = false;
		finished = false;
		terminated = false;
		timedout = false;

		exitValue = -1; // should only be 0 if successful!
	}

	/**
	 * Get the key for this task.
	 * 
	 * @return the key for this task.
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Get the command that will be run.
	 * 
	 * @return a String Array of the command.
	 */
	public String[] getCommand() {
		return cmd;
	}

	/**
	 * Check if standard output will be captured when the command in this task
	 * is executed.
	 * 
	 * @return true if standard error will be captured, false if not.
	 */
	public boolean captureStandardOutput() {
		return captureStdOut;
	}

	/**
	 * Check if standard error will be captured when the command in this task is
	 * executed.
	 * 
	 * @return true if standard error will be captured, false if not.
	 */
	public boolean captureStandardError() {
		return captureStdErr;
	}

	/**
	 * Get the task timeout. The command running on the hadoop cluster will be
	 * terminated if it does not complete in this amount of time (in ms).
	 * 
	 * @return the task timeout.
	 */
	public long getTimeout() {
		return timeout;
	}

	/**
	 * Set the task timeout. The command running on the hadoop cluster will be
	 * terminated if it does not complete in this amount of time (in ms).
	 * 
	 * @param timeout
	 *            the task timeout (in ms).
	 * @throws IllegalStateException
	 *             if the task has already been started.
	 */
	public void setTimeout(long timeout) {
		if (started) {
			throw new IllegalStateException(
					"Cannot change timeout after task has been started.");
		}
		this.timeout = timeout;
	}

	/**
	 * Marks the task as having been started.
	 * 
	 * @throws IllegalStateException
	 *             if the task has already been started.
	 */
	public void markAsStarted() {
		if (started) {
			throw new IllegalStateException("Task has alredy been started.");
		}
		started = true;
	}

	/**
	 * Checks to see if this HadoopGridTask has been started.
	 * 
	 * @return true if this HadoopGridTask has been started and false if not.
	 */
	public boolean wasStarted() {
		return started;
	}

	/**
	 * Marks the task as finished. This method should only be used for tasks
	 * that successfully completed or failed (i.e. a valid exit value can be
	 * provided). If the task was terminated or timed out, the markAsTerminated
	 * or markAsTimedOut method should be used. Those methods will take care of
	 * also marking the task as finished, without setting the exit value.
	 * 
	 * @see #markAsTerminated()
	 * @see #markAsTimedout()
	 * 
	 * @param exitValue
	 *            the exit value from the task.
	 * 
	 * @throws IllegalStateException
	 *             if the task has not been started.
	 */
	public void markAsFinished(byte exitValue) {
		if (!started) {
			throw new IllegalStateException(
					"Task has not yet been started, so it cannot be finished.");
		}
		finished = true;

		/*
		 * This should be the only place that exitValue is changed. This ensures
		 * that the task has finished and thus that the exit value actually has
		 * meaning.
		 */
		this.exitValue = exitValue;
	}

	/**
	 * Checks to see if this HadoopGridTask is finished.
	 * 
	 * @return true if this HadoopGridTask has finished.
	 */
	public boolean hasFinished() {
		return finished;
	}

	/**
	 * Check if this task HadoopGridTask is still running. The task is running
	 * if it has been started but has not yet finished.
	 * 
	 * @return true if running, false if not.
	 */
	public boolean isRunning() {
		return started && !finished;
	}

	/**
	 * Marks the task as terminated. A task that is marked as terminated is also
	 * marked as finished.
	 * 
	 * @throws IllegalStateException
	 *             if the task has not been started or has already finished.
	 */
	public void markAsTerminated() {
		if (!started || finished) {
			throw new IllegalStateException(
					"Task has not started or has already finished, it cannot be marked terminated.");
		}
		terminated = true;
		finished = true;
	}

	/**
	 * Checks to see if this HadoopGridTask was terminated. A task is considered
	 * to have been terminated if the terminate method in the HadoopGridTask
	 * caused it to be terminated or if it has timed out.
	 * 
	 * @return true if the task was terminated.
	 */
	public boolean wasTerminated() {
		return terminated;
	}

	/**
	 * Marks the task as having timed out. A task that is marked as timed out is
	 * also considered to have finished and terminated.
	 * 
	 * @throws IllegalStateException
	 *             if the task has not been started or has already finished.
	 */
	public void markAsTimedout() {
		if (!started || finished) {
			throw new IllegalStateException(
					"Task has not started or has already finished, it cannot be marked timedout.");
		}
		markAsTerminated();
		timedout = true;
	}

	/**
	 * Checks to see if this HadoopGridTask has timed out.
	 * 
	 * @return true if the task timed out.
	 */
	public boolean hasTimedout() {
		return timedout;
	}

	/**
	 * Checks if the execution of this HadoopGridTask was successful. The
	 * execution of the task is successful if it has finished execution (i.e.
	 * not terminated or timed out) and has an exit value of 0.
	 * 
	 * @return true if the execution of the task was successful, false if not
	 *         (Including if the task has not yet been started or finished).
	 */
	public boolean wasSuccessful() {
		return finished && (exitValue == 0);
	}

	/**
	 * Get the exit value generated by the execution of the command line on the
	 * Hadoop Cluster.
	 * 
	 * @return the exit value generated by executing the command. This will be
	 *         -1 if the command line cannot be executed on the cluster, if the
	 *         task was terminated or timed out. In each case additional
	 *         information will be included in the Standard Error (if it is
	 *         captured).
	 * 
	 * @throws IllegalStateException
	 *             if the task is not finished.
	 */
	public byte getExitValue() throws IllegalStateException {
		if (!finished) {
			throw new IllegalStateException(
					"Cannot get the exit value if task is not finished.");
		}

		return exitValue;
	}

	/**
	 * Get the output that was written to standard output during the execution
	 * of the command.
	 * 
	 * @return the output written to standard output. This will be the empty
	 *         string if not standard output was not captured (because of the
	 *         flag or because the task was terminated or timed out before
	 *         producing any standard output).
	 * 
	 * @throws IllegalStateException
	 *             if the task is not finished.
	 */
	public String getStandardOutput() throws IllegalStateException {
		if (!finished) {
			throw new IllegalStateException(
					"Cannot get standard output, task is not finished.");
		}

		return stdOutput;
	}

	/**
	 * Set the standard output generated by the execution of the command. If
	 * standard output is not being captured or the parameter is null, the
	 * standard output is set to the empty string.
	 * 
	 * @param stdOutput
	 *            the standard output generated
	 */
	public void setStandardOutput(String stdOutput) {
		if (!captureStdOut || stdOutput == null) {
			this.stdOutput = "";
		} else {
			this.stdOutput = stdOutput;
		}
	}

	/**
	 * Get the output that was written to standard error during the execution of
	 * the command.
	 * 
	 * @return the output written to standard error. This will be the empty
	 *         string if not standard error was not captured (because of the
	 *         flag). If the task was terminated or timed out additional
	 *         information may be provided if standard error was captured.
	 * 
	 * @throws IllegalStateException
	 *             if the task is not finished.
	 */
	public String getStandardError() throws IllegalStateException {
		if (!finished) {
			throw new IllegalStateException(
					"Cannot get standard error, task is not finished.");
		}

		return stdError;
	}

	/**
	 * Set the standard error generated by the execution of the command. If
	 * standard error is not being captured or the parameter is null, the
	 * standard error is set to the empty string.
	 * 
	 * @param stdError
	 *            the standard output generated
	 */
	public void setStandardError(String stdError) throws IllegalStateException {
		if (!captureStdErr || stdError == null) {
			this.stdError = "";
		} else {
			this.stdError = stdError;
		}
	}
}