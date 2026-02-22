package models

case class User(name: String, roles: Set[String] = Set.empty)
