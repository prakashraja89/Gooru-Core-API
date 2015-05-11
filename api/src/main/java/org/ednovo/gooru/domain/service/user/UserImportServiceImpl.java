package org.ednovo.gooru.domain.service.user;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.ednovo.gooru.core.api.model.User;
import org.ednovo.gooru.core.api.model.UserGroupSupport;
import org.ednovo.gooru.core.constant.Constants;
import org.ednovo.gooru.core.exception.NotFoundException;
import org.ednovo.gooru.domain.service.userManagement.UserManagementService;
import org.ednovo.goorucore.application.serializer.JsonDeserializer;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import au.com.bytecode.opencsv.CSVReader;

@Service
public class UserImportServiceImpl extends FileImporter implements UserImportService {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserImportServiceImpl.class);
	
	@Autowired
	private UserManagementService userManagementService;

	@Override
	public void createUser(String filename, User apiCaller, HttpServletRequest request) {
		final String mediaFileName = UserGroupSupport.getUserOrganizationNfsInternalPath() + Constants.UPLOADED_MEDIA_FOLDER + '/' + filename;
		List<String> keys = null;
		StringBuffer json = new StringBuffer();
		CSVReader csvReader=null;
		File file = null;
		try {
			file = new File(mediaFileName);
			csvReader = new CSVReader(new FileReader(file));
			String[] row = null;
			while ((row = csvReader.readNext()) != null) {
				if (keys == null) {
					keys = getJsonKeys(row);
				} else {
					String data = formInputJson(row, json, keys).toString();
					JSONObject jsonObj = requestData(generateJSONInput(data, UNDER_SCORE));
					final User user = this.buildUserFromInputParameters((getValue(USER, jsonObj)));
					this.getUserManagementService().createUserWithValidation(user, jsonObj.get(PASSWORD).toString(), null, null, false, false, apiCaller, null, jsonObj.get(DATEOFBIRTH).toString(), null, jsonObj.get(GENDER).toString(), null, null, data, false, request, null, null);
					json.setLength(0);
				}
			}
		} catch (FileNotFoundException e) {
			throw new NotFoundException(generateErrorMessage(GL0056, FILE), GL0056);
		} catch (Exception e) {
			LOGGER.error(_ERROR, e);
		} finally {
			try {
				if (file.exists()) {
					csvReader.close();
					file.delete();
				}
			} catch (Exception e) {
				LOGGER.error(_ERROR, e);
			}
		}
	}

	private User buildUserFromInputParameters(String data) {
		return JsonDeserializer.deserialize(data, User.class);
	}

	public UserManagementService getUserManagementService() {
		return userManagementService;
	}
}
