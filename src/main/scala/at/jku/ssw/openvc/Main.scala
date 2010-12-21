/*
 *     OpenVC, an open source VHDL compiler/simulator
 *     Copyright (C) 2010  Christian Reisinger
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.jku.ssw.openvc

import at.jku.ssw.openvc.VHDLCompiler.Configuration
import at.jku.ssw.openvs.VHDLRuntime.VHDLRuntimeException
import at.jku.ssw.openvs.Simulator
import java.io.{PrintWriter, FilenameFilter, File}

object Main {
  def main(arguments: Array[String]) {
    try {
      /*
      val (c, _) = parseCommandLineArguments(arguments)
      //ASTBuilder.fromFile("""C:\Users\christian\Desktop\grlib-gpl-1.0.22-b4095\lib\fmf\fifo\idt7202.vhd""", c.get)
      val x=ASTBuilder.fromFile("""C:\Users\christian\Desktop\OpenVC\vhdlSrc\alu_tb.vhd""", c.get)._2
      println(x)
      require(x.size==0)
      val filter = new FilenameFilter() {
        override def accept(dir: File, name: String): Boolean = (name.endsWith(".vhd") || name.endsWith(".vhdl")) && !name.endsWith("in.vhd")
      }
      for (i <- 0 to 10) {
        val start = System.currentTimeMillis
        c.foreach{
          configuration =>
            def toLines(sourceFile: String) = scala.io.Source.fromFile(sourceFile).getLines().toIndexedSeq
            listFiles(new File("""C:\Users\christian\Desktop\grlib-gpl-1.0.22-b4095\"""), filter, true).map{
              fileX =>
                val file = fileX.getAbsolutePath
                //println(file)
                ASTBuilder.fromFile(file, configuration)._2 match {
                  case Seq() =>
                  case messages =>
                    val sourceLines = toLines(file)
                    messages.foreach{
                      msg =>
                        println("--" + file + ": line:" + msg.position.line + " col:" + msg.position.charPosition + " " + msg.message)
                        println(sourceLines(msg.position.line - 1).toLowerCase)
                        println((" " * msg.position.charPosition) + "^")
                    }
                }
            }
        }
        println("time:" + (System.currentTimeMillis - start))
      }
      return
      */
      val (configurationOption, files) = parseCommandLineArguments(arguments)
      configurationOption.foreach{
        configuration =>
          def toLines(sourceFile: String) = scala.io.Source.fromFile(sourceFile).getLines().toIndexedSeq
          if (configuration.parseOnly) {
            files.map{
              file =>
                println(file)
                ASTBuilder.fromFile(file, configuration)._2 match {
                  case Seq() =>
                  case messages =>
                    val sourceLines = toLines(file)
                    messages.foreach{
                      msg =>
                        println("--" + file + ": line:" + msg.position.line + " col:" + msg.position.charPosition + " " + msg.message)
                        println(sourceLines(msg.position.line - 1).toLowerCase)
                        println((" " * msg.position.charPosition) + "^")
                    }
                }
            }
          } else {
            val classFilter = new FilenameFilter() {
              override def accept(dir: File, name: String): Boolean = name.endsWith(".class")
            }
            files.map(file => VHDLCompiler.compileFile(file, configuration)).foreach(result => result.printErrors(new PrintWriter(System.out), Some(toLines(result.sourceFile))))
            Simulator.loadFiles(this.getClass.getClassLoader, configuration.outputDirectory, listFiles(new File(configuration.libraryOutputDirectory), classFilter, true).map(file => file.getPath.substring(file.getPath.indexOf('\\') + 1).split('.').head.replace('\\', '.')), List("std.jar", "ieee.jar"))
            Simulator.runClass(this.getClass.getClassLoader, configuration.outputDirectory, configuration.designLibrary + ".alu_tb_body", "main$1106182723", List("std.jar", "ieee.jar"))
          }
      }
    } catch {
      case ex@(_: java.lang.reflect.InvocationTargetException | _: java.lang.ExceptionInInitializerError) if (ex.getStackTrace.exists(element => element != null && (element.getFileName.endsWith(".vhd") || element.getFileName.endsWith(".vhdl")))) =>
        ex.getCause match {
          case exception@(_: VHDLRuntimeException | _: java.lang.NullPointerException) =>
            exception.setStackTrace(exception.getStackTrace.filterNot(element => element.getFileName == null || element.getFileName.endsWith(".scala") || element.getFileName.endsWith(".java")))
            exception.printStackTrace()
          case t => t.printStackTrace()
        }
      case e: Exception => e.printStackTrace()
    }
  }

  def listFiles(directory: File, filter: FilenameFilter, recursive: Boolean): Seq[File] =
    directory.listFiles().toSeq.flatMap{
      case entry if (filter.accept(directory, entry.getName())) => Seq(entry)
      case entry if (recursive && entry.isDirectory()) => listFiles(entry, filter, recursive)
      case _ => Seq()
    }

  def parseCommandLineArguments(arguments: Array[String]): (Option[Configuration], Seq[String]) = {
    import org.apache.commons.cli._
    val options = new Options()
    options.addOption("h", "help", false, "print this message")
    options.addOption("v", "version", false, "print the version information and exit")
    options.addOption("d", "debugCompiler", false, "prints compiler debugging information.")
    options.addOption("D", "debugCodeGenerator", false, "prints the generated byte code to stdout")
    options.addOption("p", "parseOnly", false, "parses only the souce code, no code will be generated")
    options.addOption("a", "ams", false, "enables ams in the parser")

    val libraryNameOption = new Option("l", "libraryName", true, "the design library to which this design units belongs")
    libraryNameOption.setArgName("libraryName")
    options.addOption(libraryNameOption)

    val outputDirectoryOption = new Option("o", "outputDirectory", true, "the directory where the generated code will be stored")
    outputDirectoryOption.setArgName("directory")
    options.addOption(outputDirectoryOption)

    val libraryDirectoryOption = new Option("lib", "libraryDirectory", true, "the directory where existing libraries are stored")
    libraryDirectoryOption.setArgName("directory")
    options.addOption(libraryDirectoryOption)

    try {
      val parser = new PosixParser();
      val line = parser.parse(options, arguments)

      if (line.hasOption("help")) {
        new HelpFormatter().printHelp(160, "OpenVC {files}", "options:", options, "", true)
        (None, Seq())
      } else if (line.hasOption("version")) {
        println("OpenVC 0.1 Copyright (C) 2010  Christian Reisinger")
        (None, Seq())
      } else {
        val directory = line.getOptionValue("outputDirectory", "output")
        val libraryDirectory = line.getOptionValue("libraryDirectory", "vhdlLibs")
        (Some(new Configuration(line.hasOption("ams"), line.hasOption("parseOnly"), if (directory.last == File.separatorChar) directory else directory + File.separator, line.getOptionValue("libraryName", "work"),
          if (libraryDirectory.last == File.separatorChar) libraryDirectory else libraryDirectory + File.separator, line.hasOption("debugCompiler"), line.hasOption("debugCodeGenerator"))), line.getArgs.toSeq)
      }
    }
    catch {
      case exp: ParseException =>
        System.out.println("Unexpected exception:" + exp.getMessage())
        (None, Seq())
    }
  }
}

/*
         ____________                           ___
        /  _________/\                         /  /\
       /  /\________\/_________   _________   /  / /_________
      /  /_/______   /  ______/\ /_____   /\ /  / //_____   /\
     /________   /\ /  /\_____\/_\____/  / //  / /_\____/  / /
     \______ /  / //  / /      /  ___   / //  / //  ___   / /
   _________/  / //  /_/___   /  /__/  / //  / //  /__/  / /
  /___________/ //________/\ /________/ //__/ //________/ /
  \___________\/ \________\/ \________\/ \__\/ \________\/

            ______________________________________________________________
         | / ____________    ____________________   ___    __________  /\
        _|/ /  _________/\  /_/_/_/_/_/_/_/_/_/_/  /  /\  /_/_/_/_/_/ / /
       | / /  /\________\/_________   _________   /  / /_________    / /
      _|/ /  /_/______   /  ______/\ /_____   /\ /  / //_____   /\  / /
     | / /________   /\ /  /\_____\/_\____/  / //  / /_\____/  / / / /
    _|/  \______ /  / //  / /      /  ___   / //  / //  ___   / / / /
   | / _________/  / //  /_/___   /  /__/  / //  / //  /__/  / / / /
  _|/ /___________/ //________/\ /________/ //__/ //________/ / / /
 | /  \___________\/ \________\/ \________\/ \__\/ \________\/ / /
 |/___________________________________________________________/ /
  \___________________________________________________________\/

                                                    ___
                                                  /  /\
MyClass      _________   _________   _________   /  / /_________
            /  ______/\ /  ______/\ /_____   /\ /  / //_____   /\
           /  /_____ \//  /\_____\/_\____/  / //  / /_\____/  / /
          /_____   /\ /  / /      /  ___   / //  / //  ___   / /
   ___   ______/  / //  /_/___   /  /__/  / //  / //  /__/  / /
  /__/\ /________/ //________/\ /________/ //__/ //________/ /
  \__\/ \________\/ \________\/ \________\/ \__\/ \________\/


         ________  __________________________________________________
      / ____  /\  __________________________________________________
     /_______/  \  __________________________________________________
     \_______\ \ \  __________________________________________________
     / ____  / /\ \  ____________   _____________________   ___   _____
    /_______/ /\/ / /  _________/\   ___________________   /  /\   _____
    \_______\ \/ / /  /\________\/_________   _________   /  / /_________
    / ____  / / / /  /_/______   /  ______/\ /_____   /\ /  / //_____   /\
   /_______/ / / /________   /\ /  /\_____\/_\____/  / //  / /_\____/  / /
   \_______\  /  \______ /  / //  / /      /  ___   / //  / //  ___   / /
   / ____  / / _________/  / //  /_/___   /  /__/  / //  / //  /__/  / /
  /_______/ / /___________/ //________/\ /________/ //__/ //________/ /
  \_______\/  \___________\/ \________\/ \________\/ \__\/ \________\/
    ________________________________________________________________
     ______________________________________________________________
      ____________________________________________________________
      */