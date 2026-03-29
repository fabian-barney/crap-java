package media.barney.crapjava.core;

import org.jspecify.annotations.Nullable;

record MethodMetrics(
        String methodName,
        String className,
        int complexity,
        @Nullable Double coveragePercent,
        @Nullable Double crapScore
) {
}

/* mutate4java-manifest
version=1
moduleHash=c14c616559fc225dfb95013ef98f43b109edb2e857dddcf59a696e2913d45ddf
scope.0.id=Y2xhc3M6TWV0aG9kTWV0cmljcyNNZXRob2RNZXRyaWNzOjM
scope.0.kind=class
scope.0.startLine=3
scope.0.endLine=10
scope.0.semanticHash=2eab30ca44c535e25c50c68c88abb446d3e4d9052b239530ef10b931b7226641
scope.1.id=ZmllbGQ6TWV0aG9kTWV0cmljcyNjbGFzc05hbWU6NQ
scope.1.kind=field
scope.1.startLine=5
scope.1.endLine=5
scope.1.semanticHash=f443a976f0a493ec25f6b5bed022df2129866874b971248fe85fdcfa673a2d24
scope.2.id=ZmllbGQ6TWV0aG9kTWV0cmljcyNjb21wbGV4aXR5OjY
scope.2.kind=field
scope.2.startLine=6
scope.2.endLine=6
scope.2.semanticHash=525e126091077815ac3ca3d9daf9dcd2873354cc1204c1bec369d5ea3c1d61d8
scope.3.id=ZmllbGQ6TWV0aG9kTWV0cmljcyNjb3ZlcmFnZVBlcmNlbnQ6Nw
scope.3.kind=field
scope.3.startLine=7
scope.3.endLine=7
scope.3.semanticHash=6fe17226f74508c76f9b743c1a6bddd144acaf4ede3520d84fc04db2216ec6e4
scope.4.id=ZmllbGQ6TWV0aG9kTWV0cmljcyNjcmFwU2NvcmU6OA
scope.4.kind=field
scope.4.startLine=8
scope.4.endLine=8
scope.4.semanticHash=9d5b6e046cc82ebfadf65a303be821e5e42fb4a4e76edb31f40fddf770d598c2
scope.5.id=ZmllbGQ6TWV0aG9kTWV0cmljcyNtZXRob2ROYW1lOjQ
scope.5.kind=field
scope.5.startLine=4
scope.5.endLine=4
scope.5.semanticHash=af57a516a4dc672e52803affabb2baeab6f17bb9399d92de1a2445b6d00b5bc5
scope.6.id=bWV0aG9kOk1ldGhvZE1ldHJpY3MjY3Rvcig1KToz
scope.6.kind=method
scope.6.startLine=1
scope.6.endLine=10
scope.6.semanticHash=d7b8e2a24a7ae4a8bec62381c9fb1a7d2f5f505fe539e0237c58c274a8e5a8c4
*/
