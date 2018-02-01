// Import the utility functionality.
import jobs.generation.*;

// The input project name
def project = GithubProject

// The input branch name (e.g. master)
def branch = GithubBranchName

// What this repo is using for its machine images at the current time
def imageVersionMap = ['Windows_NT':'latest-dev15-5',
                       'OSX10.13':'latest-or-auto',
                       'Ubuntu14.04':'latest-or-auto',
                       'Ubuntu16.04':'20170731']

[true, false].each { isPR ->
    ['Windows_NT', 'OSX10.13', 'Ubuntu14.04', 'Ubuntu16.04'].each {osName ->
        def runtimes = ['CoreCLR']

        if (osName == 'Windows_NT') {
            runtimes.add('Full')
        }

        // TODO: make this !windows once Mono 5.0+ is available in an OSX image
        // if (osName.startsWith('Ubuntu')) {
        //     runtimes.add('Mono')
        //     runtimes.add('MonoTest')
        // }

        runtimes.each { runtime ->
            def newJobName = Utilities.getFullJobName("innerloop_${osName}_${runtime}", isPR)
            def skipTestsWhenResultsNotFound = true

            // Create a new job with the specified name.  The brace opens a new closure
            // and calls made within that closure apply to the newly created job.
            def newJob = job(newJobName) {
                description('')
            }

            // Define job.
            switch(osName) {
                case 'Windows_NT':
                    newJob.with{
                        steps{
                            // Protect against VsDevCmd behaviour of changing the current working directory https://developercommunity.visualstudio.com/content/problem/26780/vsdevcmdbat-changes-the-current-working-directory.html
                            def script = "pushd %CD% && call \"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Enterprise\\Common7\\Tools\\VsDevCmd.bat\" && popd"

                            //  Should the build be Release?  The default is Debug
                            if (runtime == "Full") {
                                script += " && build\\cibuild.cmd"
                            }
                            else if (runtime == "CoreCLR") {
                                script += " && build\\cibuild.cmd -hostType Core"
                            }

                            batchFile(script)
                        }

                        skipTestsWhenResultsNotFound = false
                    }

                    break;
                case 'OSX10.13':
                    newJob.with{
                        steps{
                            def buildCmd = "./build/cibuild.sh"

                            if (runtime == "Mono") {
                                // tests are failing on mono right now
                                buildCmd += " --scope Compile"
                            }

                            if (runtime.startsWith("Mono")) {
                                // Redundantly specify target to override
                                // "MonoTest" which cibuild.sh doesn't know
                                buildCmd += " --host Mono --target Mono"
                            }

                            shell(buildCmd)
                        }
                    }

                    break;
                case { it.startsWith('Ubuntu') }:
                    newJob.with{
                        steps{
                            def buildCmd = "./build/cibuild.sh"

                            if (runtime == "Mono") {
                                // tests are failing on mono right now
                                buildCmd += " --scope Compile"
                            }

                            if (runtime.startsWith("Mono")) {
                                // Redundantly specify target to override
                                // "MonoTest" which cibuild.sh doesn't know
                                buildCmd += " --host Mono --target Mono"
                            }

                            shell(buildCmd)
                        }
                    }

                    break;
            }

            // Add xunit result archiving. Skip if no results found.
            Utilities.addXUnitDotNETResults(newJob, 'artifacts/**/TestResults/*.xml', skipTestsWhenResultsNotFound)
            def imageVersion = imageVersionMap[osName];
            Utilities.setMachineAffinity(newJob, osName, imageVersion)
            Utilities.standardJobSetup(newJob, project, isPR, "*/${branch}")
            // Add archiving of logs (even if the build failed)
            Utilities.addArchival(newJob,
                                  'artifacts/**/log/*.binlog,artifacts/**/log/*.log,artifacts/**/Microsoft.*.UnitTests_*', /* filesToArchive */
                                  '', /* filesToExclude */
                                  false, /* doNotFailIfNothingArchived */
                                  false, /* archiveOnlyIfSuccessful */)
            // Add trigger
            if (isPR) {
                TriggerBuilder prTrigger = TriggerBuilder.triggerOnPullRequest()

                if (runtime == "MonoTest") {
                    // Until they're passing reliably, require opt in
                    // for Mono tests
                    prTrigger.setCustomTriggerPhrase("(?i).*test\\W+mono.*")
                    prTrigger.triggerOnlyOnComment()
                }

                prTrigger.triggerForBranch(branch)
                // Set up what shows up in Github:
                prTrigger.setGithubContext("${osName} Build for ${runtime}")
                prTrigger.emitTrigger(newJob)
            } else {
                if (runtime != "Mono") {
                    Utilities.addGithubPushTrigger(newJob)
                }
            }
        }
    }
}

JobReport.Report.generateJobReport(out)

// Make the call to generate the help job
Utilities.createHelperJob(this, project, branch,
    "Welcome to the ${project} Repository",  // This is prepended to the help message
    "Have a nice day!")  // This is appended to the help message.  You might put known issues here.
