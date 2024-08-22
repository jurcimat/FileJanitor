import org.hyperskill.hstest.dynamic.DynamicTest;
import org.hyperskill.hstest.stage.StageTest;
import org.hyperskill.hstest.testcase.CheckResult;
import org.hyperskill.hstest.testing.TestedProgram;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hyperskill.hstest.testing.expect.Expectation.expect;

@SuppressWarnings({"unused", "SimplifyStreamApiCallChains", "FieldCanBeLocal"})
public class FileJanitorStage5Test extends StageTest<Object> {
    private final int year = LocalDate.now().getYear();
    private final String lineOneRegex = String.format("(?i).*File\\s+Janitor\\s*,?.*\\s*%d\\s*.*", year);
    private final String lineTwoRegex = "(?i).*Powered\\s+by\\s+Bash.*";
    private final Pattern lineOne = Pattern.compile(lineOneRegex);
    private final Pattern lineTwo = Pattern.compile(lineTwoRegex);

    private final String filename = "file-janitor-help.txt";
    private final String helpFileContent = """
            File Janitor is a tool to discover files and clean directories

            Usage: file-janitor.sh [option] <file_path>

                options:
                    help            displays this help file
                    list [path]     lists files in the specified or current directory
                    report [path]   outputs a summary of files in the specified or current directory
                    clean [path]    processes (archives, deletes or moves) certain files
                                    in the specified or current directory
            """;
    private final Map<String, String> helpFile = Map.of(filename, helpFileContent);

    private final String[] unsupportedArgs = {"unknown", "-h", "unsupported", "arg1"};

    private final String noArgsHint = "Type file-janitor.sh help to see available options";

    private final String notDirectoryName = UUID.randomUUID().toString();
    private final Map<String, String> notDirectory = Map.of(notDirectoryName, "");
    private final String[] currentDir = {""};
    private final String[] pathsToList = {"../", "../../", "../../../", "test"};
    private final Map<String, String> filesToList = Map.of(
            "file1", "",
            "file2", "",
            ".file1", "",
            "file-1", "",
            "File1", "",
            "file.extension", "",
            "test/.tricky.Name", ""
    );

    private final String currentDirMessage = "Listing files in the current directory";
    private final String someDirMessage = "Listing files in ";
    private final String pathNotFoundMessage = " is not found";
    private final String notDirectoryMessage = " is not a directory";

    private final String reportLineFormat = "(?i)%d\\s+%s\\s+.+total size.*\\s+%d.*";
    private final String currentDirReportMsg = "The current directory contains:";
    private final String someDirReportMsg = " contains:";

    private final String currentDirCleanMsg = "Cleaning the current directory...";
    private final String someDirCleanMsg = "Cleaning %s...";
    private final String currentDirCleanDone = "Clean up of the current directory is complete!";
    private final String someDirCleanDone = "Clean up of %s is complete!";
    private final String cleanLineFormat = "(?i).+ done! %d files have been %s";
    private final String[] pathsToClean = {"./", "./test"};

    @DynamicTest(order = 1)
    CheckResult testScriptTitle() {
        TestedProgram program = new TestedProgram();
        String output = program.start();

        List<String> lines = expect(output).toContainAtLeast(3).lines();

        if (!program.isFinished()) {
            return CheckResult.wrong("Your script must display its title and exit.");
        }

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        return checkHint(lines);
    }

    @DynamicTest(order = 2, files = "helpFile")
    CheckResult testHelp() throws IOException {
        TestedProgram program = new TestedProgram();

        List<String> fileContent = Files.readAllLines(Path.of(filename));
        String output = program.start("help");

        List<String> lines = expect(output).toContainAtLeast(fileContent.size() + 2).lines();

        if (!program.isFinished()) {
            return CheckResult.wrong("Your script must display its title and exit.");
        }

        int helpLinesStart = lines.indexOf(fileContent.get(0));

        if (helpLinesStart == -1) {
            return CheckResult.wrong(
                    "Failed to find the first line of the help file in your script's output"
            );
        }

        List<String> infoLines = lines.subList(0, helpLinesStart);
        List<String> helpLines = lines.stream()
                .skip(helpLinesStart)
                .dropWhile(String::isBlank)
                .collect(Collectors.toList());

        CheckResult checkInfoResult = checkInfo(infoLines);

        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        return checkHelpFileContent(fileContent, helpLines);
    }

    @DynamicTest(order = 3, data = "unsupportedArgs")
    CheckResult testUnsupportedArgs(String unsupportedArg) {
        TestedProgram program = new TestedProgram();

        String output = program.start(unsupportedArg);

        List<String> lines = expect(output).toContainAtLeast(2).lines();

        if (!program.isFinished()) {
            return CheckResult.wrong("Your script must display its title and exit.");
        }

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        return checkHint(lines);
    }

    @DynamicTest(order = 4, data = "currentDir", files = "filesToList")
    CheckResult testListFilesInCurrentDir(String path) {
        TestedProgram program = new TestedProgram();

        String output = path.isBlank() ? program.start("list") : program.start("list", path);
        List<String> lines = expect(output).toContainAtLeast(3).lines();

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        if (lines.stream().noneMatch(line -> currentDirMessage.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("When listing files in the current directory, " +
                    "your script must print this line: " + currentDirMessage);
        }

        List<String> fileList = lines.stream()
                .dropWhile(line -> !currentDirMessage.equalsIgnoreCase(line.strip()))
                .skip(1)
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());

        return checkFileList(fileList, path);
    }

    @DynamicTest(order = 5)
    CheckResult testListFilesAtNonExistingPath() {
        TestedProgram program = new TestedProgram();

        String nonExistingPath = UUID.randomUUID().toString();
        String expected = nonExistingPath + pathNotFoundMessage;
        String output = program.start("list", nonExistingPath);
        List<String> lines = expect(output).toContainAtLeast(3).lines();

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        if (lines.stream().noneMatch(line -> expected.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("When listing files in a non-existing directory, " +
                    "your script must print that such directory is not found");
        }

        return CheckResult.correct();
    }

    @DynamicTest(order = 6, files = "notDirectory")
    CheckResult testListFilesAtNotDirectory() {
        TestedProgram program = new TestedProgram();

        String expected = notDirectoryName + notDirectoryMessage;
        String output = program.start("list", notDirectoryName);
        List<String> lines = expect(output).toContainAtLeast(3).lines();

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        if (lines.stream().noneMatch(line -> expected.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("If listing at a path that does not refer to a directory, " +
                    "your script must print that that path is not a directory");
        }

        return CheckResult.correct();
    }

    @DynamicTest(order = 7, data = "pathsToList", files = "filesToList")
    CheckResult testListFilesAtPath(String path) {
        TestedProgram program = new TestedProgram();

        String output = program.start("list", path);
        List<String> lines = expect(output).toContainAtLeast(3).lines();

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        String expected = someDirMessage + path;
        if (lines.stream().noneMatch(line -> expected.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("If listing at a specified path" +
                    " your script must print " + someDirMessage + " %PATH%");
        }

        List<String> fileList = lines.stream()
                .dropWhile(line -> !expected.equalsIgnoreCase(line.strip()))
                .skip(1)
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());

        return checkFileList(fileList, path);
    }

    @DynamicTest(order = 8, data = "currentDir", files = "getFilesToReport")
    CheckResult checkReportFilesAtCurrentDir(String path) {
        TestedProgram program = new TestedProgram();

        String output = path.isBlank() ? program.start("report") : program.start("report", path);
        List<String> lines = expect(output).toContainAtLeast(3).lines();

        if (lines.stream().noneMatch(line -> currentDirReportMsg.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("When reporting files in the current directory, " +
                    "your script must print this line: " + currentDirReportMsg);
        }

        List<String> fileReport = lines.stream()
                .dropWhile(line -> !currentDirReportMsg.equalsIgnoreCase(line.strip()))
                .skip(1)
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());

        if (fileReport.size() != 3) {
            return CheckResult.wrong("Your file report must contain 1 line per each file type (3 in total)");
        }

        return checkFileReport(fileReport, path);
    }

    @DynamicTest(order = 9, data = "pathsToList", files = "getFilesToReport")
    CheckResult checkReportFilesAtPath(String path) {
        TestedProgram program = new TestedProgram();

        String output = program.start("report", path);
        List<String> lines = expect(output).toContainAtLeast(3).lines();

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        String expected = path + someDirReportMsg;
        if (lines.stream().noneMatch(line -> expected.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("When reporting files in the current directory, " +
                    "your script must print this line: " + someDirReportMsg);
        }

        List<String> fileReport = lines.stream()
                .dropWhile(line -> !expected.equalsIgnoreCase(line.strip()))
                .skip(1)
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());

        if (fileReport.size() != 3) {
            return CheckResult.wrong("Your file report must contain 1 line per each file type (3 in total)");
        }

        return checkFileReport(fileReport, path);
    }

    @DynamicTest(order = 10)
    CheckResult checkReportFilesAtNonExistingPath() {
        TestedProgram program = new TestedProgram();

        String nonExistingPath = UUID.randomUUID().toString();
        String expected = nonExistingPath + pathNotFoundMessage;
        String output = program.start("report", nonExistingPath);
        List<String> lines = expect(output).toContainAtLeast(3).lines();

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        if (lines.stream().noneMatch(line -> expected.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("When reporting files in a non-existing directory, " +
                    "your script must print that such directory is not found");
        }

        return CheckResult.correct();
    }

    @DynamicTest(order = 11, files = "notDirectory")
    CheckResult checkReportFilesAtNotDirectory() {
        TestedProgram program = new TestedProgram();

        String expected = notDirectoryName + notDirectoryMessage;
        String output = program.start("report", notDirectoryName);
        List<String> lines = expect(output).toContainAtLeast(3).lines();

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        if (lines.stream().noneMatch(line -> expected.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("If reporting at a path that does not refer to a directory, " +
                    "your script must print that that path is not a directory");
        }

        return CheckResult.correct();
    }

    @DynamicTest(order = 12)
    CheckResult checkCleanFilesNoFiles() {
        TestedProgram program = new TestedProgram();

        String output = program.start("clean");
        List<String> lines = expect(output).toContainAtLeast(7).lines();

        if (lines.stream().noneMatch(line -> currentDirCleanMsg.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("When cleaning the current directory, " +
                    "your script must print this line: " + currentDirCleanMsg);
        }

        List<String> cleanReport = lines.stream()
                .dropWhile(line -> !currentDirCleanMsg.equalsIgnoreCase(line.strip()))
                .skip(1)
                .filter(line -> !line.isBlank())
                .takeWhile(it -> !currentDirCleanDone.equalsIgnoreCase(it))
                .collect(Collectors.toList());

        if (cleanReport.size() != 3) {
            return CheckResult.wrong("Your clean report must contain 1 line per each file type (3 in total)");
        }

        var expectedCount = 0;

        var logPattern = Pattern.compile(String.format(cleanLineFormat, expectedCount, "deleted"));
        var isLogReportOk = logPattern.matcher(cleanReport.get(0)).matches();
        if (!isLogReportOk) {
            return CheckResult.wrong("Your script did not output expected information about " +
                    "deleting old log files at the current dir. Expected: Deleting old log files... done! " +
                    expectedCount + " files have been deleted");
        }

        var tmpPattern = Pattern.compile(String.format(cleanLineFormat, expectedCount, "deleted"));
        var isTmpReportOk = tmpPattern.matcher(cleanReport.get(1)).matches();
        if (!isTmpReportOk) {
            return CheckResult.wrong("Your script did not output expected information about " +
                    "deleting *.tmp files at the current dir. Expected: Deleting temporary files... done! " +
                    expectedCount + " files have been deleted");
        }

        var pyPattern = Pattern.compile(String.format(cleanLineFormat, expectedCount, "moved"));
        var isPyReportOk = pyPattern.matcher(cleanReport.get(2)).matches();
        if (!isPyReportOk) {
            return CheckResult.wrong("Your script did not output expected information about " +
                    "moving *.py files at the current dir. Expected: Moving python files... done! " +
                    expectedCount + " files have been moved");
        }

        if (Files.exists(Path.of("./python_scripts"))) {
            return CheckResult.wrong(
                    "Your script must not create python_scripts directory if no *.py files were moved"
            );
        }

        return CheckResult.correct();
    }

    @DynamicTest(order = 13, data = "currentDir", files = "getFilesToReport")
    CheckResult checkCleanFilesAtCurrentDir(String path) {
        changeFilesLastModifiedDate(path);

        TestedProgram program = new TestedProgram();

        String output = path.isBlank() ? program.start("clean") : program.start("clean", path);
        List<String> lines = expect(output).toContainAtLeast(7).lines();

        if (lines.stream().noneMatch(line -> currentDirCleanMsg.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("When cleaning the current directory, " +
                    "your script must print this line: " + currentDirCleanMsg);
        }

        List<String> cleanReport = lines.stream()
                .dropWhile(line -> !currentDirCleanMsg.equalsIgnoreCase(line.strip()))
                .skip(1)
                .filter(line -> !line.isBlank())
                .takeWhile(it -> !currentDirCleanDone.equalsIgnoreCase(it))
                .collect(Collectors.toList());

        if (cleanReport.size() != 3) {
            return CheckResult.wrong("Your clean report must contain 1 line per each file type (3 in total)");
        }

        return checkCleanReport(cleanReport, path);
    }

    @DynamicTest(order = 14, data = "pathsToClean", files = "getFilesToReport")
    CheckResult checkCleanFilesAtPath(String path) {
        changeFilesLastModifiedDate(path);

        TestedProgram program = new TestedProgram();

        String output = program.start("clean", path);
        List<String> lines = expect(output).toContainAtLeast(7).lines();

        if (lines.stream().noneMatch(line -> someDirCleanMsg.formatted(path).equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("When cleaning the current directory, " +
                    "your script must print this line: " + someDirCleanMsg.formatted(path));
        }

        List<String> cleanReport = lines.stream()
                .dropWhile(line -> !someDirCleanMsg.formatted(path).equalsIgnoreCase(line.strip()))
                .skip(1)
                .filter(line -> !line.isBlank())
                .takeWhile(it -> !someDirCleanDone.formatted(path).equalsIgnoreCase(it))
                .collect(Collectors.toList());

        if (cleanReport.size() != 3) {
            return CheckResult.wrong("Your clean report must contain 1 line per each file type (3 in total)");
        }

        return checkCleanReport(cleanReport, path);
    }

    @DynamicTest(order = 15)
    CheckResult checkCleanFilesAtNonExistingPath() {
        TestedProgram program = new TestedProgram();

        String nonExistingPath = UUID.randomUUID().toString();
        String expected = nonExistingPath + pathNotFoundMessage;
        String output = program.start("clean", nonExistingPath);
        List<String> lines = expect(output).toContainAtLeast(3).lines();

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        if (lines.stream().noneMatch(line -> expected.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("When cleaning a non-existing directory, " +
                    "your script must print that such directory is not found");
        }

        return CheckResult.correct();
    }

    @DynamicTest(order = 16, files = "notDirectory")
    CheckResult checkCleanFilesAtNotDirectory() {
        TestedProgram program = new TestedProgram();

        String expected = notDirectoryName + notDirectoryMessage;
        String output = program.start("clean", notDirectoryName);
        List<String> lines = expect(output).toContainAtLeast(3).lines();

        CheckResult checkInfoResult = checkInfo(lines);
        if (!checkInfoResult.isCorrect()) {
            return checkInfoResult;
        }

        if (lines.stream().noneMatch(line -> expected.equalsIgnoreCase(line.strip()))) {
            return CheckResult.wrong("If cleaning at a path that does not refer to a directory, " +
                    "your script must print that that path is not a directory");
        }

        return CheckResult.correct();
    }

    private CheckResult checkCleanReport(List<String> cleanReport, String path) {
        var tmpCheckResult = isTmpCleanReportOk(cleanReport, path);
        if (!tmpCheckResult.isCorrect()) {
            return tmpCheckResult;
        }

        var logCheckResult = isLogCleanReportOk(cleanReport, path);
        if (!logCheckResult.isCorrect()) {
            return logCheckResult;
        }

        var pyCheckResult = isPyCleanReportOk(cleanReport, path);
        if (!pyCheckResult.isCorrect()) {
            return pyCheckResult;
        }

        return CheckResult.correct();
    }

    private CheckResult isTmpCleanReportOk(List<String> cleanReport, String path) {
        try {
            var actualTmp = getFileSizeAndCount(path, "tmp");
            var tmpActualCount = actualTmp.get("count");
            if (tmpActualCount != 0L) {
                return CheckResult.wrong("After running the script, there must not be *.tmp files in the "
                        + path + " directory, but " + tmpActualCount + " files were found");
            }

            var expectedCount = getFilenamesByExtExclPath(path, "tmp").size();
            var tmpPattern = Pattern.compile(String.format(cleanLineFormat, expectedCount, "deleted"));
            var isTmpReportOk = cleanReport.stream().anyMatch(line -> tmpPattern.matcher(line).matches());

            return isTmpReportOk ?
                    CheckResult.correct() :
                    CheckResult.wrong("Your script did not output expected information about " +
                            "deleting *.tmp files at " + path + ". Expected: Deleting temporary files... done! " +
                            expectedCount + " have been deleted");
        } catch (Exception e) {
            return CheckResult.wrong("An error happened during the test: " + e.getMessage());
        }
    }

    private CheckResult isLogCleanReportOk(List<String> cleanReport, String path) {
        try {
            var logFilesAtTargetDir = getActualLogFilesCounts(path);
            var actualNewerLogsCount = logFilesAtTargetDir.get(true).size();
            var actualOldLogsCount = logFilesAtTargetDir.get(false).size();

            if (actualOldLogsCount > 0) {
                return CheckResult.wrong("After running the script, there must be no *.log files in the "
                        + path + " directory older than 3 days, but " + actualOldLogsCount + " files were found");
            }

            var expectedLogs = getFilenamesByExtExclPath(path, "log").stream()
                    .collect(Collectors.partitioningBy(filename -> filename.matches(".*log\\d[13579]\\.log")));
            var expectedOldLogsCount = expectedLogs.get(true).size();
            var expectedNewerLogsCount = expectedLogs.get(false).size();

            if (expectedNewerLogsCount > actualNewerLogsCount) {
                return CheckResult.wrong("Your script should not delete log files that were modified " +
                        "less than 3 days ago, but " + (expectedOldLogsCount - actualNewerLogsCount)
                        + " such files were deleted");
            }

            var logPattern = Pattern.compile(String.format(cleanLineFormat, expectedOldLogsCount, "deleted"));
            var isLogReportOk = cleanReport.stream().anyMatch(line -> logPattern.matcher(line).matches());

            return isLogReportOk ?
                    CheckResult.correct() :
                    CheckResult.wrong("Your script did not output expected information about " +
                            "deleting old log files at " + path + " Expected: Deleting old log files... done! " +
                            expectedOldLogsCount + " files have been deleted");

        } catch (Exception e) {
            return CheckResult.wrong("Error happened during testing: " + e.getMessage());
        }
    }

    private CheckResult isPyCleanReportOk(List<String> cleanReport, String path) {
        try {
            var scriptsDir = path.isBlank() ? "./python_scripts" : path + "/python_scripts";
            var scriptsPath = Paths.get(scriptsDir);
            if (!Files.exists(scriptsPath)) {
                return CheckResult.wrong(scriptsDir + " is not found");
            } else if (!scriptsPath.toFile().isDirectory()) {
                return CheckResult.wrong(scriptsDir + "is not a directory");
            }

            var pyActualCountAtSourcePath = getFileSizeAndCount(path, "py").get("count");
            if (pyActualCountAtSourcePath != 0L) {
                return CheckResult.wrong("Your script didn't remove all *.py files from " + path);
            }

            var pyActualCountAtTargetPath = getFileSizeAndCount(scriptsDir, "py").get("count");
            var expectedCount = getFilenamesByExtExclPath(path, "py").size();
            if (pyActualCountAtTargetPath != (long) expectedCount) {
                return CheckResult.wrong("Your script failed to move some *.py files to " + scriptsDir);
            }

            var pyPattern = Pattern.compile(String.format(cleanLineFormat, expectedCount, "moved"));
            var isPyReportOk = cleanReport.stream().anyMatch(line -> pyPattern.matcher(line).matches());

            return isPyReportOk ?
                    CheckResult.correct() :
                    CheckResult.wrong("Your script did not output expected information about " +
                            "moving *.py files at " + path + " Expected: Moving python files... done! " +
                            expectedCount + " files have been moved");
        } catch (Exception e) {
            return CheckResult.wrong("Error happened during testing: " + e.getMessage());
        }
    }

    private CheckResult checkInfo(List<String> infoLines) {
        boolean hasLineOne = infoLines.stream()
                .anyMatch(line -> lineOne.matcher(line).matches());
        boolean hasLineTwo = infoLines.stream()
                .dropWhile(line -> !lineOne.matcher(line).matches())
                .anyMatch(line -> lineTwo.matcher(line).matches());

        if (!hasLineOne) {
            return CheckResult.wrong("Your script must output its title and the current year");
        }

        if (!hasLineTwo) {
            return CheckResult.wrong("Below the title, your script must claim that it is a Bash script");
        }

        return CheckResult.correct();
    }

    private CheckResult checkHelpFileContent(List<String> fileLines, List<String> outputLines) {
        for (int i = 0; i < fileLines.size(); i++) {
            String expected = fileLines.get(i).strip();
            String actual = outputLines.get(i).strip();
            if (!expected.equals(actual)) {
                return CheckResult.wrong(
                        "You script failed to output the content of file-janitor-help.txt correctly"
                );
            }
        }

        return CheckResult.correct();
    }

    private CheckResult checkHint(List<String> lines) {
        boolean hasCorrectHint = lines.stream()
                .dropWhile(line -> lineTwo.matcher(line).matches())
                .anyMatch(line -> noArgsHint.equalsIgnoreCase(line.strip()));

        return hasCorrectHint ?
                CheckResult.correct() :
                CheckResult.wrong("When executed with no arg or with an unsupported arg, " +
                        "your script should print a hint: Type file-janitor help to see available options");
    }

    private CheckResult checkFileList(List<String> fileList, String path) {
        try (Stream<Path> stream = Files.list(Path.of(path))) {
            List<String> actualFileList = stream
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());

            if (actualFileList.containsAll(fileList) && fileList.containsAll(actualFileList)) {
                return CheckResult.correct();
            }

            return CheckResult.wrong(
                    "Your script output incorrect list of files at "
                            + path + "\nExpected output:\n"
                            + actualFileList.stream().collect(Collectors.joining("\n"))
            );
        } catch (Exception e) {
            return CheckResult.wrong("An error occurred during testing: " + e.getMessage());
        }
    }

    private CheckResult checkFileReport(List<String> report, String path) {
        try {
            var expectedTmp = getFileSizeAndCount(path, "tmp");
            var expectedLog = getFileSizeAndCount(path, "log");
            var expectedPy = getFileSizeAndCount(path, "py");

            var tmpExpectedSize = expectedTmp.get("size");
            var tmpExpectedCount = expectedTmp.get("count");
            var logExpectedSize = expectedLog.get("size");
            var logExpectedCount = expectedLog.get("count");
            var pyExpectedSize = expectedPy.get("size");
            var pyExpectedCount = expectedPy.get("count");

            var tmpPattern = Pattern.compile(String.format(reportLineFormat, tmpExpectedCount, "tmp", tmpExpectedSize));
            var logPattern = Pattern.compile(String.format(reportLineFormat, logExpectedCount, "log", logExpectedSize));
            var pyPattern = Pattern.compile(String.format(reportLineFormat, pyExpectedCount, "py", pyExpectedSize));

            var isTmpReportOk = report.stream().anyMatch(line -> tmpPattern.matcher(line).matches());
            var isLogReportOk = report.stream().anyMatch(line -> logPattern.matcher(line).matches());
            var isPyReportOk = report.stream().anyMatch(line -> pyPattern.matcher(line).matches());

            if (!isTmpReportOk) {
                return CheckResult.wrong("Your script outputs an incorrect report for tmp files at "
                        + path + ": expected count is " + tmpExpectedCount + " and expected size is " + tmpExpectedSize);
            }

            if (!isLogReportOk) {
                return CheckResult.wrong("Your script outputs an incorrect report for log files at "
                        + path + ": expected count is " + logExpectedCount + " and expected size is " + logExpectedSize);
            }

            if (!isPyReportOk) {
                return CheckResult.wrong("Your script outputs an incorrect report for py files at "
                        + path + ": expected count is " + pyExpectedCount + " and expected size is " + pyExpectedSize);
            }

            return CheckResult.correct();
        } catch (RuntimeException e) {
            return CheckResult.wrong("An error occurred during testing: " + e.getMessage());
        }
    }

    private Map<String, Long> getFileSizeAndCount(String path, String extension) {
        try (Stream<Path> stream = Files.list(Path.of(path))) {
            List<Path> filteredFiles = stream
                    .filter(file -> !Files.isDirectory(file))
                    .filter(p -> p.getFileName().toString().endsWith("." + extension))
                    .collect(Collectors.toList());

            long size = filteredFiles.stream().mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).sum();

            long count = filteredFiles.size();

            return Map.of("count", count, "size", size);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<Boolean, List<Path>> getActualLogFilesCounts(String path) {
        try (Stream<Path> stream = Files.list(Path.of(path))) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .filter(file -> file.getFileName().toString().endsWith(".log"))
                    .collect(Collectors.partitioningBy(file -> {
                        try {
                            var lastModified = Files.readAttributes(file, BasicFileAttributes.class)
                                    .lastModifiedTime()
                                    .toInstant();
                            var timeThreshold = Instant.now()
                                    .minus(3, ChronoUnit.DAYS)
                                    .minus(1, ChronoUnit.HOURS);
                            return lastModified.isAfter(timeThreshold);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getFilenamesToReport() {
        return List.of("file.tmp", "File.tmp", ".File-1.tmp", "test/.tricky.log.tmp",
                "logfile.log", ".hidden-log-file", "test/Strange.log.file.log",
                "python-script.py", "fib2.py", "currency_conv.py", "test/another-one.py",
                "log12.log", "log21.log", "log33.log", "log40.log", "log05.log", "log06.log",
                "test/log12.log", "test/log21.log", "test/log41.log", "test/log14.log", "test/log2.log"
        );
    }

    private void changeFilesLastModifiedDate(String path) {
        try (Stream<Path> stream = Files.list(Path.of(path))) {
            var files = stream
                    .filter(file -> !Files.isDirectory(file))
                    .filter(p -> p.getFileName().toString().matches(".*log\\d[13579]\\.log"))
                    .toList();
            for (var file : files) {
                var newTime = Instant.now().minus(4, ChronoUnit.DAYS);
                var newFileTime = FileTime.from(newTime);
                var attr = Files.getFileAttributeView(file, BasicFileAttributeView.class);
                attr.setTimes(newFileTime, newFileTime, newFileTime);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> getFilesToReport() {
        return getFilenamesToReport().stream()
                .collect(Collectors.toMap(name -> name, name -> getRandomFileContent()));
    }

    private List<String> getFilenamesByExtExclPath(String path, String extension) {
        return getFilenamesToReport().stream()
                .filter(it -> {
                    if (path.isBlank() || "./".equals(path)) {
                        return !it.startsWith("test/");
                    } else {
                        return it.startsWith("test/");
                    }
                })
                .filter(it -> it.endsWith(extension))
                .collect(Collectors.toList());
    }

    private String getRandomFileContent() {
        Random rnd = ThreadLocalRandom.current();
        return rnd.ints().limit(rnd.nextInt(25, 1250))
                .mapToObj(String::valueOf)
                .collect(Collectors.joining());
    }
}
