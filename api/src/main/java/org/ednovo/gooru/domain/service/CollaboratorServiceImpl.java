/////////////////////////////////////////////////////////////
// CollaboratorServiceImpl.java
// gooru-api
// Created by Gooru on 2014
// Copyright (c) 2014 Gooru. All rights reserved.
// http://www.goorulearning.org/
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
/////////////////////////////////////////////////////////////
package org.ednovo.gooru.domain.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ednovo.gooru.application.util.MailAsyncExecutor;
import org.ednovo.gooru.application.util.TaxonomyUtil;
import org.ednovo.gooru.core.api.model.CollectionItem;
import org.ednovo.gooru.core.api.model.Content;
import org.ednovo.gooru.core.api.model.Identity;
import org.ednovo.gooru.core.api.model.InviteUser;
import org.ednovo.gooru.core.api.model.User;
import org.ednovo.gooru.core.api.model.UserContentAssoc;
import org.ednovo.gooru.core.constant.ConfigConstants;
import org.ednovo.gooru.core.exception.NotFoundException;
import org.ednovo.gooru.domain.service.setting.SettingService;
import org.ednovo.gooru.domain.service.v2.ContentService;
import org.ednovo.gooru.infrastructure.persistence.hibernate.CollectionRepository;
import org.ednovo.gooru.infrastructure.persistence.hibernate.UserRepository;
import org.ednovo.gooru.infrastructure.persistence.hibernate.collaborator.CollaboratorRepository;
import org.ednovo.gooru.infrastructure.persistence.hibernate.customTable.CustomTableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class CollaboratorServiceImpl extends BaseServiceImpl implements CollaboratorService {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CollectionRepository collectionRepository;

	@Autowired
	private CustomTableRepository customTableRepository;

	@Autowired
	private CollectionService collectionService;

	@Autowired
	private CollaboratorRepository collaboratorRepository;

	@Autowired
	private SettingService settingService;
	
	@Autowired
	private ContentService contentService;
	
	@Autowired
	private MailAsyncExecutor mailAsyncExecutor;
	
	@Override
	public List<Map<String, Object>> addCollaborator(List<String> email, String gooruOid, User apiCaller) throws Exception {
		Content content = null;
		if (gooruOid != null) {
			content = getContentRepository().findContentByGooruId(gooruOid, true);
			if (content == null) {
				throw new NotFoundException("content not found");
			}
		} else {
			throw new BadCredentialsException("content required");
		}
		List<Map<String, Object>> collaborator = new ArrayList<Map<String, Object>>();
		if (email != null) {
			for (String mailId : email) {
				Identity identity = this.getUserRepository().findByEmailIdOrUserName(mailId, true, false);
				if (identity != null) {
					UserContentAssoc userContentAssocs = this.getCollaboratorRepository().findCollaboratorById(gooruOid, identity.getUser().getGooruUId());
					if ( userContentAssocs == null) {
						UserContentAssoc userContentAssoc = new UserContentAssoc();
						userContentAssoc.setContent(content);
						userContentAssoc.setUser(identity.getUser());
						userContentAssoc.setAssociatedType("collaborator");
						userContentAssoc.setRelationship("Collaborator");
						userContentAssoc.setAssociatedBy(apiCaller);
						userContentAssoc.setLastActiveDate(new Date());
						userContentAssoc.setAssociationDate(new Date());
						this.userRepository.save(userContentAssoc);
						this.getCollectionService().createCollectionItem(content.getGooruOid(), null, new CollectionItem(), identity.getUser(), "collaborator", false);
						collaborator.add(setActiveCollaborator(userContentAssoc, "active"));
						this.getContentService().createContentPermission(content, identity.getUser());
					} else {
						collaborator.add(setActiveCollaborator(userContentAssocs, "active"));
					}

				} else {
					InviteUser inviteUsers  = this.getCollaboratorRepository().findInviteUserById(mailId, gooruOid);
					if ( inviteUsers == null) {
						InviteUser inviteUser = new InviteUser();
						inviteUser.setEmail(mailId);
						inviteUser.setGooruOid(gooruOid);
						inviteUser.setCreatedDate(new Date());
						inviteUser.setInvitationType("collaborator");
						inviteUser.setStatus(this.getCustomTableRepository().getCustomTableValue("invite_user_status", "pending"));
						this.getUserRepository().save(inviteUser);
						collaborator.add(setInviteCollaborator(inviteUser, "pending"));	
					} else {
						collaborator.add(setInviteCollaborator(inviteUsers, "pending"));
					}
					Map<String,Object> collaboratorData = new HashMap<String, Object>();
					collaboratorData.put("contentObject",content);
					collaboratorData.put("emailId",mailId);
					this.getMailAsyncExecutor().sendMailToInviteCollaborator(collaboratorData);
				}
			}
		}

		return collaborator;
	}

	private Map<String, Object> setInviteCollaborator(InviteUser inviteUser, String status) {
		Map<String, Object> listMap = new HashMap<String, Object>();
		listMap.put("emailId", inviteUser.getEmail());
		listMap.put("gooruOid", inviteUser.getGooruOid());
		listMap.put("associatedDate", inviteUser.getCreatedDate());
		if (status != null) {
			listMap.put("status", status);
		}
		return listMap;
	}

	private Map<String, Object> setActiveCollaborator(UserContentAssoc userContentAssoc, String status) {
		Map<String, Object> activeMap = new HashMap<String, Object>();
		activeMap.put("emailId", userContentAssoc.getUser().getIdentities() != null ? userContentAssoc.getUser().getIdentities().iterator().next().getExternalId() : null);
		activeMap.put("gooruUid", userContentAssoc.getUser().getGooruUId());
		activeMap.put("username", userContentAssoc.getUser().getUsername());
		activeMap.put("gooruOid", userContentAssoc.getContent().getGooruOid());
		activeMap.put("associatedDate", userContentAssoc.getAssociationDate());
		activeMap.put("profileImageUrl", settingService.getConfigSetting(ConfigConstants.PROFILE_IMAGE_URL, 0, TaxonomyUtil.GOORU_ORG_UID) + "/" + settingService.getConfigSetting(ConfigConstants.PROFILE_BUCKET, 0, TaxonomyUtil.GOORU_ORG_UID).toString() + "/" + userContentAssoc.getUser().getGooruUId()
				+ ".png");
		if (status != null) {
			activeMap.put("status", status);
		}
		return activeMap;
	}

	@Override
	public List<String> collaboratorSuggest(String text, String gooruUid) {

		return this.getCollaboratorRepository().collaboratorSuggest(text, gooruUid);
	}

	@Override
	public void deleteCollaborator(String gooruOid, List<String> email) {
		Content content = null;
		if (gooruOid != null) {
			content = getContentRepository().findContentByGooruId(gooruOid, true);
			if (content == null) {
				throw new NotFoundException("content not found");
			}
		} else {
			throw new BadCredentialsException("content required");
		}
		if (email != null) {
			for (String mailId : email) {
				Identity identity = this.getUserRepository().findByEmailIdOrUserName(mailId, true, false);
				if (identity != null) {
					UserContentAssoc userContentAssoc = this.getCollaboratorRepository().findCollaboratorById(gooruOid, identity.getUser().getGooruUId());
					CollectionItem collectionItem = this.getCollectionRepository().findCollectionByResource(gooruOid, identity.getUser().getGooruUId());
					if (userContentAssoc != null) {
						this.getCollaboratorRepository().remove(userContentAssoc);
						this.getCollectionRepository().remove(collectionItem);
						this.getContentService().deleteContentPermission(content, identity.getUser());
					}
				} else {
					InviteUser inviteUser = this.getCollaboratorRepository().findInviteUserById(mailId, gooruOid);
					if (inviteUser != null) {
						this.getCollaboratorRepository().remove(inviteUser);
					}
				}
			}
		}

	}

	@Override
	public List<Map<String, Object>> getCollaborators(String gooruOid, String filterBy) {
		List<Map<String, Object>> collaborator = new ArrayList<Map<String, Object>>();

		if (filterBy != null && filterBy.equalsIgnoreCase("active")) {
			collaborator.addAll(getActiveCollaborator(gooruOid));
		} else if (filterBy != null && filterBy.equalsIgnoreCase("pending")) {
			collaborator.addAll(getPendingCollaborator(gooruOid));
		} else {
			collaborator.addAll(getActiveCollaborator(gooruOid));
			collaborator.addAll(getPendingCollaborator(gooruOid));
		}
		return collaborator;
	}

	@Override
	public Map<String, List<Map<String, Object>>> getCollaboratorsByGroup(String gooruOid, String filterBy) {
		Map<String, List<Map<String, Object>>> collaboratorList = new HashMap<String, List<Map<String, Object>>>();
		if (filterBy != null && filterBy.equalsIgnoreCase("active")) {
			collaboratorList.put("active", getActiveCollaborator(gooruOid));
		} else if (filterBy != null && filterBy.equalsIgnoreCase("pending")) {
			collaboratorList.put("pending", getPendingCollaborator(gooruOid));
		} else {
			collaboratorList.put("active", getActiveCollaborator(gooruOid));
			collaboratorList.put("pending", getPendingCollaborator(gooruOid));
		}
		return collaboratorList;
	}

	@Override
	public List<Map<String, Object>> getActiveCollaborator(String gooruOid) {
		List<Map<String, Object>> activeList = new ArrayList<Map<String, Object>>();
		List<UserContentAssoc> userContentAssocs = this.getCollaboratorRepository().getCollaboratorsById(gooruOid);
		if (userContentAssocs != null) {
			for (UserContentAssoc userContentAssoc : userContentAssocs) {
				activeList.add(this.setActiveCollaborator(userContentAssoc, "active"));
			}
		}
		return activeList;
	}

	@Override
	public List<Map<String, Object>> getPendingCollaborator(String gooruOid) {
		List<InviteUser> inviteUsers = this.getCollaboratorRepository().getInviteUsersById(gooruOid);
		List<Map<String, Object>> pendingList = new ArrayList<Map<String, Object>>();
		if (inviteUsers != null) {
			for (InviteUser inviteUser : inviteUsers) {
				pendingList.add(this.setInviteCollaborator(inviteUser, "pending"));
			}
		}
		return pendingList;
	}
	
	@Override
	public void updateCollaboratorStatus(String mailId) throws Exception {
		List<InviteUser> inviteUsers = this.getCollaboratorRepository().getInviteUserByMail(mailId);
		for(InviteUser inviteUser : inviteUsers) {
			inviteUser.setStatus(this.getCustomTableRepository().getCustomTableValue("invite_user_status", "active"));
			inviteUser.setJoinedDate(new Date());
			this.getCollaboratorRepository().save(inviteUser);
			Identity identity = this.getUserRepository().findByEmailIdOrUserName(mailId, true, false);
			List<String> mail = new ArrayList<String>();
			mail.add(mailId);
			this.addCollaborator(mail,inviteUser.getGooruOid(), identity.getUser()) ;
		}
		
	}

	public UserRepository getUserRepository() {
		return userRepository;
	}

	public CollectionRepository getCollectionRepository() {
		return collectionRepository;
	}

	public CustomTableRepository getCustomTableRepository() {
		return customTableRepository;
	}

	public void setCollectionService(CollectionService collectionService) {
		this.collectionService = collectionService;
	}

	public CollectionService getCollectionService() {
		return collectionService;
	}

	public CollaboratorRepository getCollaboratorRepository() {
		return collaboratorRepository;
	}
	
	public ContentService getContentService() {
		return contentService;
	}

	public MailAsyncExecutor getMailAsyncExecutor() {
		return mailAsyncExecutor;
	}

}
