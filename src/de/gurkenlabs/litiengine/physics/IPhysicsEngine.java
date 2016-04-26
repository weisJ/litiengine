/***************************************************************
 * Copyright (c) 2014 - 2015 , gurkenlabs, All rights reserved *
 ***************************************************************/
package de.gurkenlabs.litiengine.physics;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import de.gurkenlabs.litiengine.entities.ICollisionEntity;
import de.gurkenlabs.litiengine.entities.IMovableEntity;

/**
 * The Interface IPhysicsEngine.
 */
public interface IPhysicsEngine {

  public void add(ICollisionEntity entity);

  public void add(Rectangle2D staticCollisionBox);

  public void clear();

  public List<Rectangle2D> getAllCollisionBoxes();

  public List<Rectangle2D> getStaticCollisionBoxes();

  /**
   * Moves the specified entity by the delta in the direction of the angle.
   *
   * @param entity
   *          the entity
   * @param angle
   *          the angle
   * @param delta
   *          the delta
   * @return true, if successful, false if the physics engine detected a
   *         collision.
   */
  public boolean move(IMovableEntity entity, float angle, float delta);

  /**
   * Moves the specified entity towards target by the delta.
   *
   * @param entity
   * @param target
   * @param delta
   * @return
   */
  public boolean move(IMovableEntity entity, Point2D target, float delta);

  public boolean move(IMovableEntity entity, float delta);

  public boolean setTurnEntityOnMove(boolean turn);

  /**
   *
   * @param rect
   * @return true if there is any collision; otherwise false.
   */
  public boolean collides(Point2D point);

  public Point2D collides(Line2D rayCast);

  /**
   *
   * @param rect
   * @return true if there is any collision; otherwise false.
   */
  public boolean collides(Rectangle2D rect);

  public void remove(ICollisionEntity entity);

  public void remove(Rectangle2D staticCollisionBox);

  public void setBounds(Rectangle2D environmentBounds);
}
