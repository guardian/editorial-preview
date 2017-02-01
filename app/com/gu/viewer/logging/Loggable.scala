package com.gu.viewer.logging

import java.security.SecureRandom

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Logger => LogbackLogger}
import com.amazonaws.auth.{InstanceProfileCredentialsProvider, STSAssumeRoleSessionCredentialsProvider}
import com.gu.logback.appender.kinesis.KinesisAppender
import com.gu.viewer.aws.{AWS, AwsInstanceTags}
import net.logstash.logback.layout.LogstashLayout
import org.slf4j.{LoggerFactory, Logger => SLFLogger}
import play.api.{Logger => PlayLogger}


trait Loggable {
  val log = play.api.Logger(getClass)
}

object Loggable extends AwsInstanceTags {
  val rootLogger = LoggerFactory.getLogger(SLFLogger.ROOT_LOGGER_NAME).asInstanceOf[LogbackLogger]

  import play.api.Play.current
  val config = play.api.Play.configuration
  val loggingPrefix = "aws.kinesis.logging"

  def init() = {
    for {
      stack <- readTag("Stack")
      app <- readTag("App")
      stage <- readTag("Stage")
      stream <- config.getString(s"$loggingPrefix.streamName")
    } yield {
      val context = rootLogger.getLoggerContext

      val layout = new LogstashLayout()
      layout.setContext(context)
      layout.setCustomFields(s"""{"stack":"$stack","app":"$app","stage":"$stage"}""")
      layout.start()

      val appender = new KinesisAppender[ILoggingEvent]()
      appender.setBufferSize(1000)
      appender.setRegion(AWS.region.getName)
      appender.setStreamName(stream)
      appender.setContext(context)
      appender.setLayout(layout)
      appender.setCredentialsProvider(buildCredentialsProvider())
      appender.start()

      rootLogger.addAppender(appender)
    }
  }

  private def buildCredentialsProvider() = {
    val stsRole = config.getString(s"$loggingPrefix.stsRoleToAssume").get

    val random = new SecureRandom()
    val sessionId = s"session${random.nextDouble()}"

    val instanceProvider = new InstanceProfileCredentialsProvider()
    new STSAssumeRoleSessionCredentialsProvider(instanceProvider, stsRole, sessionId)
  }
}
