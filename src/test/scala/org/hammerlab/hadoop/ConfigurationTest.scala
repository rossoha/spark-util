package org.hammerlab.hadoop

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }
import java.util.Properties

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{ Input, Output }
import org.apache.hadoop
import org.apache.hadoop.conf.Configuration.isDeprecated
import org.hammerlab.test.Suite
import org.hammerlab.test.matchers.seqs.MapMatcher.mapMatch

import scala.collection.JavaConverters._

class ConfigurationTest
  extends Suite {
  test("kryo serde") {
    val conf = Configuration()
    val kryo = new Kryo()
    kryo.setRegistrationRequired(true)
    val baos = new ByteArrayOutputStream()
    val output = new Output(baos)

    intercept[IllegalArgumentException] {
      kryo.writeClassAndObject(output, conf)
    }
    .getMessage should startWith("Class is not registered: org.hammerlab.hadoop.Configuration")

    Configuration.register(kryo)

    kryo.writeClassAndObject(output, conf)

    output.close()

    val bytes = baos.toByteArray

    val bais = new ByteArrayInputStream(bytes)

    val input = new Input(bais)
    val afterConf = kryo.readClassAndObject(input).asInstanceOf[Configuration]

    compare(conf, afterConf)
  }

  test("java serde") {
    val conf = Configuration()
    val baos = new ByteArrayOutputStream()
    val out = new ObjectOutputStream(baos)

    out.writeObject(conf)

    out.close()

    val bytes = baos.toByteArray

    val bais = new ByteArrayInputStream(bytes)
    val in = new ObjectInputStream(bais)

    val after = in.readObject().asInstanceOf[Configuration]

    compare(conf, after)
  }

  val propsField = classOf[hadoop.conf.Configuration].getDeclaredMethod("getProps")
  propsField.setAccessible(true)

  def compare(beforeConf: Configuration, afterConf: Configuration): Unit = {
    val before =
      propsField
        .invoke(beforeConf.value)
        .asInstanceOf[Properties]
        .asScala

    val after =
      propsField
        .invoke(afterConf.value)
        .asInstanceOf[Properties]
        .asScala
        .filterKeys(
          !isDeprecated(_)
        )

    after should mapMatch(before)
  }
}