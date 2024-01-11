rootProject.name = "FEGA-Norway"
include("lib:crypt4gh")
include("lib:clearinghouse")
include("services:mq-interceptor")
findProject(":lib:crypt4gh")?.name = "crypt4gh"
findProject(":lib:clearinghouse")?.name = "clearinghouse"
findProject(":services:mq-interceptor")?.name = "mq-interceptor"
