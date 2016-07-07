package org.lmsdbg
package utils

import java.io.File

object Utils {

  def printDebug(a: Any) = if (Main.printOn) println(a)

  /**
   * Finds all the subfolders of root with a certain name
   */
  def findFoldersNamed(root: File, name: String): Seq[File] = {
    var res = Seq.empty[File]
    if (root.isDirectory) {
      if (root.getName == name) {
        res :+= root
      }
      res ++= root.listFiles.flatMap(findFoldersNamed(_, name))
    }
    res
  }

  /**
   * Finds all the files in subfolders of root with a certain extension
   */
  def filesWithExtension(root: File, extension: String): Seq[File] =
    if (root.isDirectory) {
      root.listFiles.flatMap(filesWithExtension(_, extension))
    } else if (root.getName.endsWith("." + extension)) {
      Seq(root)
    } else {
      Seq.empty
    }
}
