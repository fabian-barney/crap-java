package media.barney.crapjava.core;

record CoverageData(int missedInstructions, int coveredInstructions) {

    double coveragePercent() {
        int total = missedInstructions + coveredInstructions;
        if (total == 0) {
            return 0.0;
        }
        return (coveredInstructions * 100.0) / total;
    }
}

/* mutate4java-manifest
version=1
moduleHash=433ba4b2abba61f792fc0181ff3b2a1f66756f0d6ef168cf5ec6e7f240f15494
scope.0.id=Y2xhc3M6Q292ZXJhZ2VEYXRhI0NvdmVyYWdlRGF0YToz
scope.0.kind=class
scope.0.startLine=3
scope.0.endLine=12
scope.0.semanticHash=87aa606976f15abf19cea4b108d5f2609ee953e9d42b6e812fdb7e05d07d10f9
scope.1.id=ZmllbGQ6Q292ZXJhZ2VEYXRhI2NvdmVyZWRJbnN0cnVjdGlvbnM6Mw
scope.1.kind=field
scope.1.startLine=3
scope.1.endLine=3
scope.1.semanticHash=4fcacc05e512f723247247f449ace2e375b58e82d3ca705adf551fa64c300594
scope.2.id=ZmllbGQ6Q292ZXJhZ2VEYXRhI21pc3NlZEluc3RydWN0aW9uczoz
scope.2.kind=field
scope.2.startLine=3
scope.2.endLine=3
scope.2.semanticHash=11d1f0843b5d83d3d4a8a1cb697dac34ecc0bcd0a4df4534587d17759a9161b3
scope.3.id=bWV0aG9kOkNvdmVyYWdlRGF0YSNjb3ZlcmFnZVBlcmNlbnQoMCk6NQ
scope.3.kind=method
scope.3.startLine=5
scope.3.endLine=11
scope.3.semanticHash=b5ca27864e09a536dd6af320d78e641fa3ce1fa6a384e2f2da4c94000bbe56df
scope.4.id=bWV0aG9kOkNvdmVyYWdlRGF0YSNjdG9yKDIpOjM
scope.4.kind=method
scope.4.startLine=1
scope.4.endLine=12
scope.4.semanticHash=8050ad331327c2af2636cf68c945923707350210aa3d77f632cb38acb645885a
*/
