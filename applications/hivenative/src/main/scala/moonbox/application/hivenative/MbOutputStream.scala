package moonbox.application.hivenative

import java.io.OutputStream
import java.nio.charset.Charset

import org.apache.spark.MbLauncherBackend
import org.apache.spark.launcher.SparkAppHandle

import scala.util.matching.Regex

class MbOutputStream extends OutputStream {
	private val launcherBackend = new MbLauncherBackend() {
		override def onStopRequest(): Unit = stop(SparkAppHandle.State.KILLED)
	}

	launcherBackend.connect()

	def stateChanged(s: String): Unit = {
		if (s != "\n") {
			s match {
				case SubmittedJob(appId) =>
					launcherBackend.setAppId(appId)
					launcherBackend.setState(SparkAppHandle.State.SUBMITTED)
				case StartingJob(_)=>
					launcherBackend.setState(SparkAppHandle.State.RUNNING)
				case EndedJob(_) =>
					launcherBackend.setState(SparkAppHandle.State.FINISHED)
				case _ =>
			}
		}
	}

	case object SubmittedJob {
		def unapply(arg: String): Option[String] = {
			val r: Regex = "Starting Job = (job_\\d+_\\d+).*".r
			arg match {
				case r(jobId) =>
					Some(jobId.replace("job", "application"))
				case _ =>
					None
			}
		}
	}

	case object StartingJob {
		def unapply(arg: String): Option[Boolean] = {
			val r: Regex = "Hadoop job information for Stage-1".r
			r.findFirstIn(arg).map(_ => true)
		}
	}

	case object EndedJob {
		def unapply(arg: String): Option[Boolean] = {
			val r: Regex = "Time taken: .*".r
			r.findFirstIn(arg).map(_ => true)
		}
	}


	override def write(b: Int): Unit = {
		System.out.write(b)
	}

	override def write(b: Array[Byte], off: Int, len: Int): Unit = {
		stateChanged(new String(b, off, len, Charset.forName("UTF-8")))
		System.out.write(b, off, len)
	}

	private def stop(finalState: SparkAppHandle.State): Unit = {
		// TODO stop running hive
		try {
			launcherBackend.setState(finalState)
		} finally {
			launcherBackend.close()
		}
	}
}
