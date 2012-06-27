cfn-update-stack LiveDashboard-PROD \
	--capabilities CAPABILITY_IAM \
	--template-file dashboard.json \
	--parameters "KeyName=id_rsa_ec2;InstanceType=m1.medium;Stage=PROD" 
	
