## invoke() - Invoke another Lambda function


```java
// Basic invoke
var result = ctx.invoke("invoke-function", 
				"function-name",
				"\"payload\"",
				Result.class, 
				InvokeConfig.builder()
						.payloadSerDes(...)  // payload serializer
						.resultSerDes(...)   // result deserializer
						.timeout(Duration.of(...))  // wait timeout
						.tenantId(...)       // Lambda tenantId
						.build()
		);
				
```