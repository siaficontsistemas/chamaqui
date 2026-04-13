package com.helpdesk.helpdesk.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.dto.profile.ProfileResponse;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class ProfileService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;

	public ProfileService(UserRepository userRepository, UserMapper userMapper) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
	}

	@Transactional(readOnly = true)
	public ProfileResponse getByEmail(String email) {
		return userRepository.findByEmailIgnoreCase(email)
			.map(userMapper::toProfileResponse)
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));
	}
}
