package handler;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

public class MainHandler implements RequestHandler<SNSEvent, Object> {

    private static final String SUBJECT = "Verify Email for Access";

    @Override
    public Object handleRequest(SNSEvent snsEvent, Context context) {

        LambdaLogger lambdaLogger = context.getLogger();
        lambdaLogger.log("Lambda function Invocation started");
        String message = snsEvent.getRecords().get(0).getSNS().getMessage();
        String[] contents = message.split("-");
        lambdaLogger.log("Event logged for the UserName: "+ contents[0]);
        lambdaLogger.log("Token for the Username: "+ contents[0] + " is Token: "+ contents[1]);

        lambdaLogger.log("Checking if this is a Duplicate invocation");
        try{
            lambdaLogger.log("Building client for DynamoDB");
            AmazonDynamoDB amazonDynamoDB =  AmazonDynamoDBClientBuilder.standard().withRegion("us-east-1").build();
            DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
            Table table = dynamoDB.getTable("emailSent");
            Item item = table.getItem("email", contents[0]);
            if(item!=null) {
                lambdaLogger.log("Email already sent for user with mail: "+contents[0]);
                lambdaLogger.log("This is a Duplicate trigger");
                return new Object();
            }
            lambdaLogger.log("Email already not sent for user with mail: "+contents[0]);
            String domainName = System.getenv("domainName");
            lambdaLogger.log("Logging the domain name used for Sending email: "+ domainName);

            String url = "http://"+domainName+"/v1/VerifyUserEmail?"+"email="+contents[0]+"&token="+contents[1];

            String HTMLBODY = "<h1>Verify your Email</h1>"
                    + "<p>This email was sent to Verify your Email Address "
                    + " <a href=" + "'" + url + "'>"
                    + "Click here</a>";

            String TEXTBODY = "This email was sent to Verify your email. Please copy and paste the below link in a Browser. "
                    + url;

            lambdaLogger.log("Url used to send mail : "+ url);
            lambdaLogger.log("New version deployed : 001");

            lambdaLogger.log("Building client for SES");
            AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                    .withRegion("us-east-1").build();
            lambdaLogger.log("Building request Object for SES");
            SendEmailRequest request = new SendEmailRequest()
                    .withDestination(new Destination().withToAddresses(contents[0]))
                    .withMessage(new Message()
                            .withBody(new Body()
                                    .withHtml(new Content()
                                            .withCharset("UTF-8").withData(HTMLBODY))
                                    .withText(new Content()
                                            .withCharset("UTF-8").withData(TEXTBODY)))
                            .withSubject(new Content()
                                    .withCharset("UTF-8").withData(SUBJECT)))
                    .withSource("verify@" + domainName);

            lambdaLogger.log("Trying to send mail");
            SendEmailResult sendEmailResult = client.sendEmail(request);
            lambdaLogger.log(sendEmailResult.getMessageId());
            lambdaLogger.log(sendEmailResult.getSdkResponseMetadata().toString());
            lambdaLogger.log("Email sent!");
            item = new Item()
                    .withPrimaryKey("email", contents[0]);
            table.putItem(item);
        }catch (MessageRejectedException e){
            lambdaLogger.log(e.getMessage());
            lambdaLogger.log("Error in sending mail");
        }
        lambdaLogger.log("Function done");
        return new Object();
    }
}
