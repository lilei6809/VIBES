package com.vibes.userservice.mapper;

import com.vibes.userservice.dto.UserResponseDTO;
import com.vibes.userservice.model.User;
import com.vibes.userservice.model.UserPhoto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Comparator;
import java.util.List;

/**
 * 将 User 映射为 UserResponseDTO, 其中配置将 List<UserPhoto> -> String avatarUrl
 */
@Mapper(componentModel = "spring")
public interface UserMapper {


    @Mapping(source = "photos", target = "avatarUrl", qualifiedByName = "mapPrimaryPhoto")
    UserResponseDTO userToUserResponseDTO(User user);

    @Named("mapPrimaryPhoto")
    default String mapPrimaryPhoto(List<UserPhoto> photos) {
        if (photos == null || photos.isEmpty()) {
            return null;
        }
        // Logic to find primary avatar: find photo with the smallest sequence number.
        return photos.stream()
                .min(Comparator.comparing(UserPhoto::getSequence))
                .map(UserPhoto::getPhotoUrl)
                .orElse(null);
    }
} 