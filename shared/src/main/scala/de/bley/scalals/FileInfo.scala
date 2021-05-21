package de.bley.scalals
package generic

import java.time.Instant
import java.nio.file.Path

trait FileInfo {
  def name: String
  def path: Path
  def isDirectory: Boolean
  def isRegularFile: Boolean
  def isSymlink: Boolean
  def isPipe: Boolean
  def isSocket: Boolean
  def isCharDev: Boolean
  def isBlockDev: Boolean
  def group: String
  def owner: String
  def permissions: Int
  def size: Long
  def lastModifiedTime: Instant
  def lastAccessTime: Instant
  def creationTime: Instant
  def isExecutable: Boolean
}
