package ru.practicum.ewm.event;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.practicum.ewm.dto.event.EventFullDto;
import ru.practicum.ewm.dto.event.EventShortDto;
import ru.practicum.ewm.dto.event.LocationDto;
import ru.practicum.ewm.dto.event.NewEventDto;
import ru.practicum.ewm.model.category.Category;
import ru.practicum.ewm.model.event.Event;
import ru.practicum.ewm.model.event.Location;
import ru.practicum.ewm.model.user.User;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "initiator", source = "user")
    @Mapping(target = "location", source = "newEventDto.location")
    @Mapping(target = "createdOn", ignore = true)
    @Mapping(target = "publishedOn", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "category", source = "newEventDto.category", qualifiedByName = "categoryFromId")
    Event toEvent(NewEventDto newEventDto, User user);

    @Named("categoryFromId")
    default Category categoryFromId(Long id) {
        if (id == null) return null;
        Category category = new Category();
        category.setId(id);
        return category;
    }

    @Mapping(target = "confirmedRequests", source = "requests")
    @Mapping(target = "views", source = "views")
    @Mapping(target = "comments", source = "comments")
    EventShortDto toShortDto(Event event, Long requests, Long views, Long comments);

    @Mapping(target = "confirmedRequests", source = "requests")
    @Mapping(target = "views", source = "views")
    @Mapping(target = "location", source = "event.location")
    @Mapping(target = "comments", source = "comments")
    EventFullDto toFullDto(Event event, Long requests, Long views, Long comments);

    Location toLocation(LocationDto locationDto);
}

