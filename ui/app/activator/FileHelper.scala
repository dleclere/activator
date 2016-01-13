/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */
package activator

import java.io._
import java.security.MessageDigest
import java.util.zip._
import java.nio.channels.FileChannel

object FileHelper {
  final val hexArray: Array[Char] = "0123456789abcdef".toCharArray

  final def bytesToHex(bytes: Array[Byte]): String = {
    val hexChars: Array[Char] = new Array[Char](bytes.length * 2)
    var j: Int = 0
    while (j < bytes.length) {
      val v = bytes(j) & 0xFF
      hexChars(j * 2) = hexArray(v >>> 4)
      hexChars(j * 2 + 1) = hexArray(v & 0x0F)
      j += 1
    }
    new String(hexChars)
  }

  def bracket[T, I, R](in: I, init: I => R, cleanup: R => Unit)(body: R => T): T = {
    val r = init(in)
    try {
      body(r)
    } finally {
      cleanup(r)
    }
  }

  def withFileInputStream[T](in: File)(body: FileInputStream => T): T =
    bracket[T, File, FileInputStream](in, (x => new FileInputStream(x)), _.close())(body)

  def withFileOutputStream[T](in: File)(body: FileOutputStream => T): T =
    bracket[T, File, FileOutputStream](in, (x => new FileOutputStream(x)), _.close())(body)

  def withZipInputStream[T](in: InputStream)(body: ZipInputStream => T): T =
    bracket[T, InputStream, ZipInputStream](in, (x => new ZipInputStream(x)), _.close())(body)

  def withFileInputChannel[T](in: FileInputStream)(body: FileChannel => T): T =
    bracket[T, FileInputStream, FileChannel](in, _.getChannel(), _.close())(body)

  def withFileOutputChannel[T](in: FileOutputStream)(body: FileChannel => T): T =
    bracket[T, FileOutputStream, FileChannel](in, _.getChannel(), _.close())(body)

  def withBufferedReader[T](in: Reader)(body: BufferedReader => T): T =
    bracket[T, Reader, BufferedReader](in, x => new BufferedReader(x), _.close())(body)

  def withFileReader[T](in: File)(body: FileReader => T): T =
    bracket[T, File, FileReader](in, x => new FileReader(x), _.close())(body)

  def writeToFile(in: Array[Byte], destination: File, replaceDestination: Boolean = true): Unit = {
    (destination.exists(), destination.isFile()) match {
      case (false, _) => Option(destination.getParentFile()).map(_.mkdirs())
      case (true, true) =>
        if (replaceDestination) destination.delete()
        else throw new IOException(s"destination: $destination exists replaceDestination is 'false'")
      case (true, false) => throw new IOException(s"destination: $destination exists and is not a file")
    }
    withFileOutputStream(destination) { fos =>
      fos.write(in)
    }
  }

  def getFiles(source: File): Set[File] = {
    if (source.isFile()) Set(source)
    else Option(source.listFiles).map { lf =>
      val (newFiles, dirs) = lf.toSeq.partition(_.isFile())
      (newFiles ++ dirs.flatMap(d => getFiles(d))).toSet
    } getOrElse (Set.empty[File])
  }

  def copyFile(source: File, destination: File, replaceDestination: Boolean = true): Unit =
    (source.exists(), source.isFile()) match {
      case (true, true) =>
        (destination.exists(), destination.isFile()) match {
          case (false, _) => Option(destination.getParentFile()).map(_.mkdirs())
          case (true, true) =>
            if (replaceDestination) destination.delete()
            else throw new IOException(s"destination: $destination exists replaceDestination is 'false'")
          case (true, false) => throw new IOException(s"destination: $destination exists and is not a file")
        }
        withFileInputStream(source) { fis =>
          withFileOutputStream(destination) { fos =>
            withFileInputChannel(fis) { fic =>
              withFileOutputChannel(fos) { foc =>
                foc.transferFrom(fic, 0, fic.size())
              }
            }
          }
        }
      case (true, false) => throw new IOException(s"source: $source is not a file")
      case (false, _) => throw new IOException(s"source file: $source does not exist")
    }

  def verifyFile(in: File,
    targetDigest: String,
    md: MessageDigest = MessageDigest.getInstance("SHA-256")): Unit = {
    withFileInputStream(in) { fis =>
      val buffer = new Array[Byte](1024)
      var readCount: Int = fis.read(buffer)
      while (readCount > 0) {
        md.update(buffer, 0, readCount)
        readCount = fis.read(buffer)
      }
      val digest = md.digest()
      val digestString = bytesToHex(digest).toLowerCase
      if (digestString != targetDigest.toLowerCase) throw new RuntimeException(s"input file: $in failed checksum.  Looking for ${targetDigest.toLowerCase} got $digestString")
    }
  }

  def zipfileEntryStream(zis: ZipInputStream): Stream[ZipEntry] = zis.getNextEntry() match {
    case null => Stream.empty[ZipEntry]
    case entry => entry #:: zipfileEntryStream(zis)
  }

  def relativeTo(root: File)(file: String): File =
    new File(root, file)

  def createTempDirectory(prefix: String, suffix: String): File = {
    val temp = File.createTempFile(prefix, suffix)
    if (!(temp.delete())) throw new IOException(s"Could not delete temp file: ${temp.getAbsolutePath}")
    if (!(temp.mkdir())) throw new IOException(s"Could not create temp directory: ${temp.getAbsolutePath}")
    temp
  }

  def deleteAll(file: File): Unit = {
    if (file.isDirectory) {
      file.list() match {
        case null =>
        case l => l.toSeq match {
          case files if files.length == 0 => file.delete()
          case files =>
            files.foreach(f => deleteAll(new File(file, f)))
            file.delete()
        }
      }
    } else {
      file.delete()
    }
  }

  def unZipFile(zipFile: File, outputFolder: File): File = {
    val buffer: Array[Byte] = new Array[Byte](1024)

    withFileInputStream(zipFile) { fis =>
      withZipInputStream(fis) { zis =>
        for (entry <- zipfileEntryStream(zis)) {
          val fileName = entry.getName()
          val newFile = new File(outputFolder, fileName)
          if (entry.isDirectory) {
            newFile.mkdirs()
          } else {
            newFile.getParentFile().mkdirs()
            withFileOutputStream(newFile) { fos =>
              var len = zis.read(buffer)
              while (len > 0) {
                fos.write(buffer, 0, len)
                len = zis.read(buffer)
              }
            }
          }
        }
      }
    }
    outputFolder
  }
}
