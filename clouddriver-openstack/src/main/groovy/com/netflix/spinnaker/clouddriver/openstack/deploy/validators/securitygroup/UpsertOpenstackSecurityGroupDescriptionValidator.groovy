/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.securitygroup

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.openstack.OpenstackOperation
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.UpsertOpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.apache.commons.lang.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

/**
 * Validates the upsert security group operation description.
 */
@OpenstackOperation(AtomicOperations.UPSERT_SECURITY_GROUP)
@Component
class UpsertOpenstackSecurityGroupDescriptionValidator extends DescriptionValidator<UpsertOpenstackSecurityGroupDescription> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, UpsertOpenstackSecurityGroupDescription description, Errors errors) {
    def validator = new OpenstackAttributeValidator('upsertOpenstackSecurityGroupAtomicOperationDescription', errors)
    validator.validateCredentials(description.account, accountCredentialsProvider)
    validator.validateNotEmpty(description.region, "region")
    if (StringUtils.isNotEmpty(description.id)) {
      validator.validateUUID(description.id, 'id')
    }
    if (!description.rules.isEmpty()) {
      description.rules.each { r ->
        validator.validateCIDR(r.cidr, 'cidr')
        validator.validatePort(r.fromPort, 'fromPort')
        validator.validatePort(r.toPort, 'toPort')
        validator.validateRuleType(r.ruleType, 'ruleType')
      }
    }
  }

}
