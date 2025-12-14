# Фича - комментарии к событиям

## Private

### <code>POST /events/{eventId}/comments</code>

#### Праметры

* Запрос на добавление нового комментария к событию с указанным <code>eventId</code>.
* В запросе должен быть заголовок <code>X-Ewm-User-Id</code> с указанием <code>userId</code> пользователя, который оставляет комментарий.

#### Тело

* В <code>body</code> передаётся <code>CommentDto</code> с заполненным полем <code>text</code>.

#### Ответы

* <code>201</code> Комментарий успешно создан, возвращается <code>CommentDto</code> со всеми полями (<code>edited = false</code>, <code>editedOn = null</code>).
* <code>400</code> Запрос составлен некорректно (отсутствует текст комментария, не указан заголовок).
* <code>404</code> Пользователь или событие не найдены.

### <code>PATCH /events/{eventId}/comments/{commentId}</code>

#### Параметры

* Запрос на изменение комментария с <code>commentId</code> к событию <code>eventId</code>.
* В запросе должен быть заголовок <code>X-Ewm-User-Id</code> с указанием <code>userId</code> пользователя, который редактирует комментарий.

#### Тело

* В <code>body</code> передаётся <code>UpdateCommentRequest</code> с заполненным полем <code>text</code>.

#### Ответы

* <code>200</code> Комментарий успешно изменён, возвращается <code>CommentDto</code> со всеми полями (<code>edited = true</code>, <code>editedOn != null</code>).
* <code>400</code> Запрос составлен некорректно (отсутствует текст комментария, не указан заголовок).
* <code>403</code> Нет доступа на редактирование комментария (<code>X-Ewm-User-Id != userId</code>).
* <code>404</code> Пользователь, событие или комментарий не найдены.

### <code>DELETE /events/{eventId}/comments/{commentId}</code>

#### Параметры

* Запрос на удаления комментария с <code>commentId</code> к событию <code>eventId</code>.
* В запросе должен быть заголовок <code>X-Ewm-User-Id</code> с указанием <code>userId</code> пользователя, который удаляет комментарий.

#### Ответы

* <code>204</code> Комментарий успешно удалён.
* <code>400</code> Запрос составлен некорректно (не указан заголовок).
* <code>403</code> Нет доступа на удаление комментария (<code>X-Ewm-User-Id != userId</code>).
* <code>404</code> Пользователь, событие или комментарий не найдены.

## Admin

### <code>DELETE /admin/events/{eventId}/comments/{commentId}</code>

#### Параметры

* Запрос администратора на удаления комментария с <code>commentId</code> к событию <code>eventId</code>.

#### Ответы

* <code>204</code> Комментарий успешно удалён.
* <code>404</code> Событие или комментарий не найдены.

## Public

### <code>GET /events/{eventId}/comments</code>

#### Параметры

* Получить все комментарии к событию <code>eventId</code>.

#### Ответы

* <code>200</code> Массив объектов <code>CommentDto</code>.
* <code>404</code> Событие не найдено.

### Изменение существующих эндпоинтов

* При вызове эндпоинтов получения событий в возвращаемые <code>dto</code> добавлены массивы комментариев к событиям.
* Если запрашивается конкретное событие по <code>eventId</code>, в <code>dto</code> события присутствует массив <code>CommentDto</code>.
* Если запрашивается список событий, в <code>dto</code> каждого события присутствует массив <code>CommentShortDto</code>.

## Модель данных

### <code>CommentDto</code>

```json
{
  "id": 1,
  "text": "текст комментария",
  "userId": 2,
  "eventId": 3,
  "createdOn": "2022-09-06T21:10:05.432",
  "edited": false,
  "editedOn": null
}
```

Поле <code>edited</code> устанавливается в <code>true</code>, если комментарий был отредактирован.
В поле <code>editedOn</code> устанавливается последняя дата редактирования комментария.

### <code>CommentShortDto</code>

```json
{
  "id": 1,
  "text": "текст комментария",
  "userId": 2
}
```

### <code>UpdateCommentRequest</code>

```json
{
  "text": "текст комментария"
}
```