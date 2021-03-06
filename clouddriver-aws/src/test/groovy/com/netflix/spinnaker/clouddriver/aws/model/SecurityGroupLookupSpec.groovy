package com.netflix.spinnaker.clouddriver.aws.model

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult
import com.amazonaws.services.ec2.model.IpPermission
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest
import com.amazonaws.services.ec2.model.SecurityGroup
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import spock.lang.Specification
import spock.lang.Subject

class SecurityGroupLookupSpec extends Specification {

  final amazonEC2 = Mock(AmazonEC2)
  final amazonClientProvider = Stub(AmazonClientProvider) {
    getAmazonEC2(_, "us-east-1", _) >> amazonEC2
  }
  final accountCredentialsRepository = Stub(AccountCredentialsRepository) {
    getAll() >> [
      Stub(NetflixAmazonCredentials) {
        getName() >> "test"
        getAccountId() >> "id-test"
      },
      Stub(NetflixAmazonCredentials) {
        getName() >> "prod"
        getAccountId() >> "id-prod"
      }
    ]
  }

  final securityGroupLookupFactory = new SecurityGroupLookupFactory(amazonClientProvider,
    accountCredentialsRepository)

  @Subject
  final securityGroupLookup = securityGroupLookupFactory.getInstance("us-east-1")

  void "should create security group"() {
    when:
    final result = securityGroupLookup.createSecurityGroup(
      new UpsertSecurityGroupDescription(
        credentials: Stub(NetflixAmazonCredentials) {
          getName() >> "test"
        },
        vpcId: "vpc-1",
        name: "wideOpen",
        description: "desc",
        securityGroupIngress: []
      )
    )

    then:
    1 * amazonEC2.createSecurityGroup(new CreateSecurityGroupRequest(
      groupName: "wideOpen",
      description: "desc",
      vpcId: "vpc-1"
    )) >> new CreateSecurityGroupResult(
      groupId: "sg-123"
    )

    then:
    result.securityGroup == new SecurityGroup(ownerId: "id-test", groupId: "sg-123", groupName: "wideOpen", vpcId: "vpc-1", description: "desc")
  }

  void "should look up security group"() {
    when:
    final result = securityGroupLookup.getSecurityGroupByName("test", "wideOpen", "vpc-1")

    then:
    1 * amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult(
      securityGroups: [
             new SecurityGroup(ownerId: "id-test", groupId: "sg-123", groupName: "wideOpen", vpcId: "vpc-1")
      ]
    )

    then:
    result.securityGroup == new SecurityGroup(ownerId: "id-test", groupId: "sg-123", groupName: "wideOpen", vpcId: "vpc-1")

  }

  void "should look up security group, but not call AWS again"() {
    when:
    final result = securityGroupLookup.getSecurityGroupByName("test", "wideOpen", "vpc-1")

    then:
    1 * amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult(
      securityGroups: [
        new SecurityGroup(ownerId: "id-test", groupId: "sg-123", groupName: "wideOpen", vpcId: "vpc-1")
      ]
    )
    result.securityGroup == new SecurityGroup(ownerId: "id-test", groupId: "sg-123", groupName: "wideOpen", vpcId: "vpc-1")

    when:
    result = securityGroupLookup.getSecurityGroupByName("test", "wideOpen", "vpc-1")

    then:
    result.securityGroup == new SecurityGroup(ownerId: "id-test", groupId: "sg-123", groupName: "wideOpen", vpcId: "vpc-1")
    0 * _
  }

  void "should return null on look up when security group does not exist"() {
    when:
    final result = securityGroupLookup.getSecurityGroupByName("test", "wideOpen", "vpc-1")

    then:
    1 * amazonEC2.describeSecurityGroups(_) >> new DescribeSecurityGroupsResult(
      securityGroups: [
        new SecurityGroup(groupId: "sg-456", groupName: "NotTheGroupYouWereLokkingFor", vpcId: "vpc-1")
      ]
    )

    then:
    result == null
    0 * _
  }

  void "should add and remove ingress"() {
    final securityGroupUpdater = new SecurityGroupLookupFactory.SecurityGroupUpdater(
      new SecurityGroup(groupId: "sg-123"), amazonEC2
    )

    when:
    securityGroupUpdater.addIngress([new IpPermission(fromPort: 999)])

    then:
    1 * amazonEC2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest(
      groupId: "sg-123",
      ipPermissions: [new IpPermission(fromPort: 999)]
    ))
    0 * _

    when:
    securityGroupUpdater.removeIngress([new IpPermission(fromPort: 111)])

    then:
    1 * amazonEC2.revokeSecurityGroupIngress(new RevokeSecurityGroupIngressRequest(
      groupId: "sg-123",
      ipPermissions: [new IpPermission(fromPort: 111)]
    ))

  }

}
